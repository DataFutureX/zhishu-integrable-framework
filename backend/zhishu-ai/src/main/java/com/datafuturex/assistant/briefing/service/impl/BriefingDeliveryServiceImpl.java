package com.datafuturex.assistant.briefing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datafuturex.assistant.briefing.domain.entity.AiBriefingDeliveryEntity;
import com.datafuturex.assistant.briefing.domain.vo.BriefingDeliveryVO;
import com.datafuturex.assistant.briefing.domain.vo.BriefingStatsVO;
import com.datafuturex.assistant.briefing.mapper.AiBriefingDeliveryMapper;
import com.datafuturex.assistant.briefing.service.BriefingDeliveryService;
import com.datafuturex.assistant.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BriefingDeliveryServiceImpl implements BriefingDeliveryService {

    private final AiBriefingDeliveryMapper deliveryMapper;

    @Override
    public AiBriefingDeliveryEntity createPending(Long scheduleId, String triggerType, String triggerRef,
                                                  String userId, Long agentId, String title) {
        AiBriefingDeliveryEntity entity = new AiBriefingDeliveryEntity();
        entity.setScheduleId(scheduleId);
        entity.setTriggerType(triggerType);
        entity.setTriggerRef(triggerRef);
        entity.setUserId(userId);
        entity.setAgentId(agentId);
        entity.setTitle(title);
        entity.setStatus("PENDING");
        entity.setEmailStatus("NONE");
        entity.setWebhookStatus("NONE");
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        deliveryMapper.insert(entity);
        return entity;
    }

    @Override
    public void markRunning(Long deliveryId) {
        AiBriefingDeliveryEntity entity = require(deliveryId);
        entity.setStatus("RUNNING");
        entity.setStartedAt(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        deliveryMapper.updateById(entity);
    }

    @Override
    public void markSuccess(Long deliveryId, Long runId, String contentMd) {
        AiBriefingDeliveryEntity entity = require(deliveryId);
        entity.setStatus("SUCCESS");
        entity.setRunId(runId);
        entity.setContentMd(contentMd);
        entity.setFinishedAt(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        // M1：站内铃仅打标时间，实际推送留给后续里程碑
        if (entity.getBellNotifiedAt() == null) {
            entity.setBellNotifiedAt(LocalDateTime.now());
        }
        deliveryMapper.updateById(entity);
    }

    @Override
    public void markFailed(Long deliveryId, String errorMessage) {
        AiBriefingDeliveryEntity entity = require(deliveryId);
        entity.setStatus("FAILED");
        entity.setErrorMessage(truncate(errorMessage, 2000));
        entity.setFinishedAt(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        deliveryMapper.updateById(entity);
    }

    @Override
    public void updateEmailStatus(Long deliveryId, String status, String emailTo, String error) {
        AiBriefingDeliveryEntity entity = require(deliveryId);
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : "NONE";
        entity.setEmailStatus(normalized);
        if (emailTo != null) {
            entity.setEmailTo(truncate(emailTo, 512));
        }
        entity.setEmailError(truncate(error, 2000));
        if ("SENT".equals(normalized)) {
            entity.setEmailSentAt(LocalDateTime.now());
        }
        entity.setUpdateTime(LocalDateTime.now());
        deliveryMapper.updateById(entity);
    }

    @Override
    public void updateWebhookStatus(Long deliveryId, String status, String error) {
        AiBriefingDeliveryEntity entity = require(deliveryId);
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : "NONE";
        entity.setWebhookStatus(normalized);
        entity.setWebhookError(truncate(error, 2000));
        if ("SENT".equals(normalized)) {
            entity.setWebhookSentAt(LocalDateTime.now());
        }
        entity.setUpdateTime(LocalDateTime.now());
        deliveryMapper.updateById(entity);
    }

    @Override
    public AiBriefingDeliveryEntity requireEntity(Long id) {
        return require(id);
    }

    @Override
    public BriefingDeliveryVO getForUser(Long id, String userId) {
        AiBriefingDeliveryEntity entity = require(id);
        assertOwner(entity, userId);
        return toVo(entity);
    }

    @Override
    public Page<BriefingDeliveryVO> pageByUser(String userId, String status, int pageNum, int pageSize) {
        int pn = Math.max(pageNum, 1);
        int ps = Math.min(Math.max(pageSize, 1), 100);
        LambdaQueryWrapper<AiBriefingDeliveryEntity> countQw = baseUserWrapper(userId, status);
        long total = deliveryMapper.selectCount(countQw);
        long offset = (long) (pn - 1) * ps;
        List<AiBriefingDeliveryEntity> records = deliveryMapper.selectList(
                baseUserWrapper(userId, status)
                        .orderByDesc(AiBriefingDeliveryEntity::getCreateTime)
                        .last("LIMIT " + ps + " OFFSET " + offset));
        Page<BriefingDeliveryVO> voPage = new Page<>(pn, ps, total);
        voPage.setRecords(records.stream().map(this::toVo).toList());
        return voPage;
    }

    private LambdaQueryWrapper<AiBriefingDeliveryEntity> baseUserWrapper(String userId, String status) {
        LambdaQueryWrapper<AiBriefingDeliveryEntity> qw = new LambdaQueryWrapper<AiBriefingDeliveryEntity>()
                .eq(AiBriefingDeliveryEntity::getUserId, userId);
        if (StringUtils.hasText(status)) {
            qw.eq(AiBriefingDeliveryEntity::getStatus, status.trim().toUpperCase());
        }
        return qw;
    }

    @Override
    public BriefingDeliveryVO latestSuccess(String userId) {
        AiBriefingDeliveryEntity entity = deliveryMapper.selectOne(new LambdaQueryWrapper<AiBriefingDeliveryEntity>()
                .eq(AiBriefingDeliveryEntity::getUserId, userId)
                .eq(AiBriefingDeliveryEntity::getStatus, "SUCCESS")
                .orderByDesc(AiBriefingDeliveryEntity::getCreateTime)
                .last("LIMIT 1"));
        return entity == null ? null : toVo(entity);
    }

    @Override
    public List<BriefingDeliveryVO> recent(String userId, int limit) {
        int lim = Math.max(1, Math.min(limit, 50));
        return deliveryMapper.selectList(new LambdaQueryWrapper<AiBriefingDeliveryEntity>()
                        .eq(AiBriefingDeliveryEntity::getUserId, userId)
                        .orderByDesc(AiBriefingDeliveryEntity::getCreateTime)
                        .last("LIMIT " + lim))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public void markRead(Long id, String userId) {
        AiBriefingDeliveryEntity entity = require(id);
        assertOwner(entity, userId);
        if (entity.getReadAt() != null) {
            return;
        }
        entity.setReadAt(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        deliveryMapper.updateById(entity);
    }

    @Override
    public long unreadCount(String userId) {
        return deliveryMapper.selectCount(new LambdaQueryWrapper<AiBriefingDeliveryEntity>()
                .eq(AiBriefingDeliveryEntity::getUserId, userId)
                .eq(AiBriefingDeliveryEntity::getStatus, "SUCCESS")
                .isNull(AiBriefingDeliveryEntity::getReadAt));
    }

    @Override
    public BriefingStatsVO stats(String userId) {
        long total = deliveryMapper.selectCount(new LambdaQueryWrapper<AiBriefingDeliveryEntity>()
                .eq(AiBriefingDeliveryEntity::getUserId, userId));
        long success = deliveryMapper.selectCount(new LambdaQueryWrapper<AiBriefingDeliveryEntity>()
                .eq(AiBriefingDeliveryEntity::getUserId, userId)
                .eq(AiBriefingDeliveryEntity::getStatus, "SUCCESS"));
        long failed = deliveryMapper.selectCount(new LambdaQueryWrapper<AiBriefingDeliveryEntity>()
                .eq(AiBriefingDeliveryEntity::getUserId, userId)
                .eq(AiBriefingDeliveryEntity::getStatus, "FAILED"));
        long unread = unreadCount(userId);
        long pendingOrRunning = deliveryMapper.selectCount(new LambdaQueryWrapper<AiBriefingDeliveryEntity>()
                .eq(AiBriefingDeliveryEntity::getUserId, userId)
                .in(AiBriefingDeliveryEntity::getStatus, List.of("PENDING", "RUNNING")));
        return new BriefingStatsVO(total, success, failed, unread, pendingOrRunning);
    }

    private AiBriefingDeliveryEntity require(Long id) {
        AiBriefingDeliveryEntity entity = deliveryMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "简报投递不存在: " + id);
        }
        return entity;
    }

    private void assertOwner(AiBriefingDeliveryEntity entity, String userId) {
        if (userId == null || !userId.equals(entity.getUserId())) {
            throw new BusinessException(403, "无权访问该简报");
        }
    }

    private BriefingDeliveryVO toVo(AiBriefingDeliveryEntity e) {
        return new BriefingDeliveryVO(
                e.getId(), e.getScheduleId(), e.getTriggerType(), e.getTriggerRef(), e.getUserId(),
                e.getAgentId(), e.getRunId(), e.getTitle(), e.getContentMd(), e.getStatus(),
                e.getErrorMessage(), e.getStartedAt(), e.getFinishedAt(), e.getReadAt(),
                e.getEmailStatus(), e.getCreateTime());
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
