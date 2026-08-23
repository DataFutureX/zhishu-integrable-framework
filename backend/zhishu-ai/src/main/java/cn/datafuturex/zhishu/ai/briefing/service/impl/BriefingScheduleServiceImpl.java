package cn.datafuturex.zhishu.ai.briefing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.datafuturex.zhishu.ai.briefing.domain.dto.BriefingScheduleUpsertDTO;
import cn.datafuturex.zhishu.ai.briefing.domain.entity.AiBriefingScheduleEntity;
import cn.datafuturex.zhishu.ai.briefing.domain.vo.BriefingScheduleVO;
import cn.datafuturex.zhishu.ai.briefing.mapper.AiBriefingScheduleMapper;
import cn.datafuturex.zhishu.ai.briefing.service.BriefingScheduleService;
import cn.datafuturex.zhishu.ai.briefing.support.BriefingScheduleTimeSupport;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BriefingScheduleServiceImpl implements BriefingScheduleService {

    private final AiBriefingScheduleMapper scheduleMapper;

    @Override
    public List<BriefingScheduleVO> listEnabled() {
        return scheduleMapper.selectList(new LambdaQueryWrapper<AiBriefingScheduleEntity>()
                        .eq(AiBriefingScheduleEntity::getEnabled, true)
                        .orderByAsc(AiBriefingScheduleEntity::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public List<BriefingScheduleVO> listAll() {
        return scheduleMapper.selectList(new LambdaQueryWrapper<AiBriefingScheduleEntity>()
                        .orderByAsc(AiBriefingScheduleEntity::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public BriefingScheduleVO get(Long id) {
        return toVo(requireEntity(id));
    }

    @Override
    public BriefingScheduleVO create(BriefingScheduleUpsertDTO dto) {
        AiBriefingScheduleEntity entity = new AiBriefingScheduleEntity();
        applyDto(entity, dto);
        entity.setCreatedBy(UserContext.getUsername());
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setCronExpr(compileCron(entity));
        entity.setNextRunAt(computeNextRunAt(entity, now));
        scheduleMapper.insert(entity);
        return toVo(entity);
    }

    @Override
    public BriefingScheduleVO update(Long id, BriefingScheduleUpsertDTO dto) {
        AiBriefingScheduleEntity entity = requireEntity(id);
        applyDto(entity, dto);
        entity.setUpdateTime(LocalDateTime.now());
        entity.setCronExpr(compileCron(entity));
        entity.setNextRunAt(computeNextRunAt(entity, LocalDateTime.now()));
        scheduleMapper.updateById(entity);
        return toVo(entity);
    }

    @Override
    public AiBriefingScheduleEntity requireEntity(Long id) {
        AiBriefingScheduleEntity entity = scheduleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "简报调度不存在: " + id);
        }
        return entity;
    }

    @Override
    public List<AiBriefingScheduleEntity> findDue(LocalDateTime now) {
        return scheduleMapper.selectList(new LambdaQueryWrapper<AiBriefingScheduleEntity>()
                .eq(AiBriefingScheduleEntity::getEnabled, true)
                .isNotNull(AiBriefingScheduleEntity::getNextRunAt)
                .le(AiBriefingScheduleEntity::getNextRunAt, now)
                .orderByAsc(AiBriefingScheduleEntity::getNextRunAt)
                .last("LIMIT 20"));
    }

    @Override
    public void markRan(AiBriefingScheduleEntity schedule, LocalDateTime ranAt) {
        schedule.setLastRunAt(ranAt);
        schedule.setNextRunAt(computeNextRunAt(schedule, ranAt));
        schedule.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(schedule);
    }

    @Override
    public LocalDateTime computeNextRunAt(AiBriefingScheduleEntity schedule, LocalDateTime from) {
        return BriefingScheduleTimeSupport.computeNextRunAt(
                schedule.getScheduleType(),
                schedule.getScheduleTime(),
                schedule.getScheduleDays(),
                schedule.getCronExpr(),
                schedule.getTimezone(),
                from);
    }

    @Override
    public String compileCron(AiBriefingScheduleEntity schedule) {
        return BriefingScheduleTimeSupport.compileCron(
                schedule.getScheduleType(),
                schedule.getScheduleTime(),
                schedule.getScheduleDays(),
                schedule.getCronExpr());
    }

    private void applyDto(AiBriefingScheduleEntity entity, BriefingScheduleUpsertDTO dto) {
        entity.setName(dto.name().trim());
        entity.setAgentId(dto.agentId());
        entity.setPromptTemplate(dto.promptTemplate());
        entity.setScopeType(StringUtils.hasText(dto.scopeType()) ? dto.scopeType().trim() : "USER_PROJECTS");
        entity.setScheduleType(dto.scheduleType().trim().toUpperCase());
        entity.setScheduleTime(dto.scheduleTime());
        entity.setScheduleDays(dto.scheduleDays());
        entity.setCronExpr(dto.cronExpr());
        entity.setTimezone(StringUtils.hasText(dto.timezone()) ? dto.timezone().trim() : "Asia/Shanghai");
        entity.setNotifyBell(dto.notifyBell() == null || dto.notifyBell());
        entity.setNotifyEmail(Boolean.TRUE.equals(dto.notifyEmail()));
        entity.setEmailToMode(StringUtils.hasText(dto.emailToMode()) ? dto.emailToMode().trim() : "USER_PROFILE");
        entity.setEmailExtraTo(dto.emailExtraTo());
        entity.setEmailSubjectTemplate(dto.emailSubjectTemplate());
        entity.setEnabled(dto.enabled() == null || dto.enabled());
    }

    private BriefingScheduleVO toVo(AiBriefingScheduleEntity e) {
        return new BriefingScheduleVO(
                e.getId(), e.getName(), e.getAgentId(), e.getPromptTemplate(), e.getScopeType(),
                e.getScheduleType(), e.getScheduleTime(), e.getScheduleDays(), e.getCronExpr(), e.getTimezone(),
                e.getNextRunAt(), e.getLastRunAt(), e.getNotifyBell(), e.getNotifyEmail(),
                e.getEmailToMode(), e.getEmailExtraTo(), e.getEmailSubjectTemplate(), e.getEnabled(),
                e.getCreatedBy(), e.getCreateTime(), e.getUpdateTime());
    }
}
