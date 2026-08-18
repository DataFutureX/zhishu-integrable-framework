package cn.datafuturex.yunqi.biz.announcement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.datafuturex.yunqi.common.PageResult;
import cn.datafuturex.yunqi.common.SecurityUtils;
import cn.datafuturex.yunqi.biz.announcement.dto.AnnouncementCreateDTO;
import cn.datafuturex.yunqi.biz.announcement.dto.AnnouncementQueryDTO;
import cn.datafuturex.yunqi.biz.announcement.dto.AnnouncementUpdateDTO;
import cn.datafuturex.yunqi.biz.announcement.entity.AnnouncementEntity;
import cn.datafuturex.yunqi.biz.announcement.entity.AnnouncementReadEntity;
import cn.datafuturex.yunqi.modules.entity.UserEntity;
import cn.datafuturex.yunqi.biz.announcement.mapper.AnnouncementMapper;
import cn.datafuturex.yunqi.biz.announcement.mapper.AnnouncementReadMapper;
import cn.datafuturex.yunqi.biz.announcement.service.AnnouncementService;
import cn.datafuturex.yunqi.biz.announcement.service.AnnouncementSseService;
import cn.datafuturex.yunqi.modules.constant.PermissionConstants;
import cn.datafuturex.yunqi.modules.service.UserService;
import cn.datafuturex.yunqi.biz.announcement.vo.AnnouncementVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统公告服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_REVOKED = 2;

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementReadMapper announcementReadMapper;
    private final UserService userService;
    private final AnnouncementSseService announcementSseService;

    @Override
    public PageResult<AnnouncementVO> pageQueryForAdmin(AnnouncementQueryDTO query) {
        assertAdmin();
        return doPageQuery(query, null);
    }

    @Override
    public PageResult<AnnouncementVO> pageQueryPublished(AnnouncementQueryDTO query) {
        return doPageQuery(query, STATUS_PUBLISHED);
    }

    @Override
    public List<AnnouncementVO> listRecentPublished(int limit) {
        int size = limit > 0 ? Math.min(limit, 50) : 10;
        LambdaQueryWrapper<AnnouncementEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnnouncementEntity::getStatus, STATUS_PUBLISHED)
                .orderByDesc(AnnouncementEntity::getPublishTime)
                .last("LIMIT " + size);
        Set<Long> readIds = loadReadAnnouncementIds(requireCurrentUserId());
        return announcementMapper.selectList(wrapper).stream()
                .map(entity -> toVO(entity, readIds.contains(entity.getId())))
                .toList();
    }

    @Override
    public long countUnread() {
        Long userId = requireCurrentUserId();
        Set<Long> readIds = loadReadAnnouncementIds(userId);

        LambdaQueryWrapper<AnnouncementEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnnouncementEntity::getStatus, STATUS_PUBLISHED);
        if (!readIds.isEmpty()) {
            wrapper.notIn(AnnouncementEntity::getId, readIds);
        }
        return announcementMapper.selectCount(wrapper);
    }

    @Override
    public Optional<AnnouncementVO> findById(Long id) {
        AnnouncementEntity entity = announcementMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        if (entity.getStatus() != STATUS_PUBLISHED && !isCurrentUserAdmin()) {
            throw new RuntimeException("无权查看该公告");
        }
        Long userId = getCurrentUserId();
        boolean read = userId != null && loadReadAnnouncementIds(userId).contains(id);
        return Optional.of(toVO(entity, read));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementEntity create(AnnouncementCreateDTO dto) {
        assertAdmin();
        UserEntity publisher = requireCurrentUser();

        AnnouncementEntity entity = new AnnouncementEntity();
        entity.setTitle(dto.title().trim());
        entity.setContent(dto.content().trim());
        entity.setPriority(dto.priority());
        entity.setPublisherId(publisher.getId());
        entity.setPublisherName(resolveDisplayName(publisher));
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());

        if (Boolean.TRUE.equals(dto.publishImmediately())) {
            applyPublished(entity, publisher);
        } else {
            entity.setStatus(STATUS_DRAFT);
        }

        announcementMapper.insert(entity);
        log.info("创建公告成功: id={}, title={}", entity.getId(), entity.getTitle());

        if (entity.getStatus() == STATUS_PUBLISHED) {
            announcementSseService.pushAnnouncement(toVO(entity, false));
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementEntity update(AnnouncementUpdateDTO dto) {
        assertAdmin();
        AnnouncementEntity entity = requireEntity(dto.id());
        if (entity.getStatus() == STATUS_PUBLISHED) {
            throw new RuntimeException("已发布的公告不可编辑，请先撤回");
        }

        if (StringUtils.hasText(dto.title())) {
            entity.setTitle(dto.title().trim());
        }
        if (StringUtils.hasText(dto.content())) {
            entity.setContent(dto.content().trim());
        }
        if (dto.priority() != null) {
            entity.setPriority(dto.priority());
        }
        entity.setUpdateTime(LocalDateTime.now());
        announcementMapper.updateById(entity);
        log.info("更新公告成功: id={}", entity.getId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        assertAdmin();
        AnnouncementEntity entity = requireEntity(id);
        if (entity.getStatus() == STATUS_PUBLISHED) {
            throw new RuntimeException("已发布的公告不可删除，请先撤回");
        }

        LambdaQueryWrapper<AnnouncementReadEntity> readWrapper = new LambdaQueryWrapper<>();
        readWrapper.eq(AnnouncementReadEntity::getAnnouncementId, id);
        announcementReadMapper.delete(readWrapper);
        announcementMapper.deleteById(id);
        log.info("删除公告成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementEntity publish(Long id) {
        assertAdmin();
        AnnouncementEntity entity = requireEntity(id);
        if (entity.getStatus() == STATUS_PUBLISHED) {
            throw new RuntimeException("公告已发布");
        }

        boolean rePublishing = entity.getStatus() == STATUS_REVOKED;

        UserEntity publisher = requireCurrentUser();
        applyPublished(entity, publisher);
        entity.setUpdateTime(LocalDateTime.now());
        announcementMapper.updateById(entity);

        if (rePublishing) {
            LambdaQueryWrapper<AnnouncementReadEntity> readWrapper = new LambdaQueryWrapper<>();
            readWrapper.eq(AnnouncementReadEntity::getAnnouncementId, id);
            announcementReadMapper.delete(readWrapper);
        }

        log.info("发布公告成功: id={}, title={}, rePublish={}", entity.getId(), entity.getTitle(), rePublishing);

        announcementSseService.pushAnnouncement(toVO(entity, false));
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementEntity revoke(Long id) {
        assertAdmin();
        AnnouncementEntity entity = requireEntity(id);
        if (entity.getStatus() != STATUS_PUBLISHED) {
            throw new RuntimeException("仅已发布的公告可撤回");
        }
        entity.setStatus(STATUS_REVOKED);
        entity.setUpdateTime(LocalDateTime.now());
        announcementMapper.updateById(entity);
        log.info("撤回公告成功: id={}", id);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long id) {
        AnnouncementEntity entity = requireEntity(id);
        if (entity.getStatus() != STATUS_PUBLISHED) {
            throw new RuntimeException("仅已发布的公告可标记已读");
        }

        Long userId = requireCurrentUserId();
        LambdaQueryWrapper<AnnouncementReadEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnnouncementReadEntity::getAnnouncementId, id)
                .eq(AnnouncementReadEntity::getUserId, userId);
        if (announcementReadMapper.selectCount(wrapper) > 0) {
            return;
        }

        AnnouncementReadEntity readEntity = new AnnouncementReadEntity();
        readEntity.setAnnouncementId(id);
        readEntity.setUserId(userId);
        readEntity.setReadTime(LocalDateTime.now());
        announcementReadMapper.insert(readEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllAsRead() {
        Long userId = requireCurrentUserId();
        Set<Long> readIds = loadReadAnnouncementIds(userId);

        LambdaQueryWrapper<AnnouncementEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnnouncementEntity::getStatus, STATUS_PUBLISHED)
                .select(AnnouncementEntity::getId);
        if (!readIds.isEmpty()) {
            wrapper.notIn(AnnouncementEntity::getId, readIds);
        }

        List<AnnouncementEntity> unreadList = announcementMapper.selectList(wrapper);
        LocalDateTime now = LocalDateTime.now();
        for (AnnouncementEntity entity : unreadList) {
            AnnouncementReadEntity readEntity = new AnnouncementReadEntity();
            readEntity.setAnnouncementId(entity.getId());
            readEntity.setUserId(userId);
            readEntity.setReadTime(now);
            announcementReadMapper.insert(readEntity);
        }
        return unreadList.size();
    }

    private PageResult<AnnouncementVO> doPageQuery(AnnouncementQueryDTO query, Integer fixedStatus) {
        Page<AnnouncementEntity> page = new Page<>(query.pageNum(), query.pageSize());
        LambdaQueryWrapper<AnnouncementEntity> wrapper = buildQueryWrapper(query, fixedStatus);
        wrapper.orderByDesc(AnnouncementEntity::getPublishTime)
                .orderByDesc(AnnouncementEntity::getCreateTime);

        Page<AnnouncementEntity> resultPage = announcementMapper.selectPage(page, wrapper);
        Long userId = getCurrentUserId();
        Set<Long> readIds = userId != null ? loadReadAnnouncementIds(userId) : Collections.emptySet();

        List<AnnouncementVO> records = resultPage.getRecords().stream()
                .map(entity -> toVO(entity, readIds.contains(entity.getId())))
                .toList();

        PageResult<AnnouncementVO> pageResult = new PageResult<>();
        pageResult.setCurrent(resultPage.getCurrent());
        pageResult.setSize(resultPage.getSize());
        pageResult.setTotal(resultPage.getTotal());
        pageResult.setPages(resultPage.getPages());
        pageResult.setRecords(records);
        return pageResult;
    }

    private LambdaQueryWrapper<AnnouncementEntity> buildQueryWrapper(AnnouncementQueryDTO query, Integer fixedStatus) {
        LambdaQueryWrapper<AnnouncementEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.title())) {
            wrapper.like(AnnouncementEntity::getTitle, query.title());
        }
        if (query.priority() != null) {
            wrapper.eq(AnnouncementEntity::getPriority, query.priority());
        }
        if (fixedStatus != null) {
            wrapper.eq(AnnouncementEntity::getStatus, fixedStatus);
        } else if (query.status() != null) {
            wrapper.eq(AnnouncementEntity::getStatus, query.status());
        }
        if (query.startTime() != null) {
            wrapper.ge(AnnouncementEntity::getPublishTime, query.startTime());
        }
        if (query.endTime() != null) {
            wrapper.le(AnnouncementEntity::getPublishTime, query.endTime());
        }
        if (Boolean.TRUE.equals(query.unreadOnly())) {
            Long userId = requireCurrentUserId();
            Set<Long> readIds = loadReadAnnouncementIds(userId);
            wrapper.eq(AnnouncementEntity::getStatus, STATUS_PUBLISHED);
            if (!readIds.isEmpty()) {
                wrapper.notIn(AnnouncementEntity::getId, readIds);
            }
        }
        return wrapper;
    }

    private Set<Long> loadReadAnnouncementIds(Long userId) {
        LambdaQueryWrapper<AnnouncementReadEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnnouncementReadEntity::getUserId, userId)
                .select(AnnouncementReadEntity::getAnnouncementId);
        return announcementReadMapper.selectList(wrapper).stream()
                .map(AnnouncementReadEntity::getAnnouncementId)
                .collect(Collectors.toSet());
    }

    private AnnouncementEntity requireEntity(Long id) {
        AnnouncementEntity entity = announcementMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("公告不存在: id=" + id);
        }
        return entity;
    }

    private void assertAdmin() {
        if (!isCurrentUserAdmin()) {
            throw new RuntimeException("仅管理员可操作公告");
        }
    }

    private boolean isCurrentUserAdmin() {
        String username = SecurityUtils.getCurrentUsername();
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return userService.findByUsername(username)
                .map(user -> PermissionConstants.ROLE_ADMIN.equalsIgnoreCase(user.getRole()))
                .orElse(false);
    }

    private UserEntity requireCurrentUser() {
        String username = SecurityUtils.getCurrentUsername();
        if (!StringUtils.hasText(username)) {
            throw new RuntimeException("未登录");
        }
        return userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    private Long requireCurrentUserId() {
        return requireCurrentUser().getId();
    }

    private Long getCurrentUserId() {
        String username = SecurityUtils.getCurrentUsername();
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return userService.findByUsername(username).map(UserEntity::getId).orElse(null);
    }

    private void applyPublished(AnnouncementEntity entity, UserEntity publisher) {
        entity.setStatus(STATUS_PUBLISHED);
        entity.setPublishTime(LocalDateTime.now());
        entity.setPublisherId(publisher.getId());
        entity.setPublisherName(resolveDisplayName(publisher));
    }

    private String resolveDisplayName(UserEntity user) {
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName();
        }
        return user.getUsername();
    }

    private AnnouncementVO toVO(AnnouncementEntity entity, boolean read) {
        return new AnnouncementVO(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getPriority(),
                entity.getStatus(),
                entity.getPublishTime(),
                entity.getPublisherId(),
                entity.getPublisherName(),
                read,
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
