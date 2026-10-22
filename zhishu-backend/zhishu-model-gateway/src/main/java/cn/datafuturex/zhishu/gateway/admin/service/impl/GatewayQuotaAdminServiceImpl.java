package cn.datafuturex.zhishu.gateway.admin.service.impl;

import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import cn.datafuturex.zhishu.gateway.admin.dto.GatewayQuotaUpsertDTO;
import cn.datafuturex.zhishu.gateway.admin.service.GatewayQuotaAdminService;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayQuotaVO;
import cn.datafuturex.zhishu.gateway.quota.GatewayQuotaEntity;
import cn.datafuturex.zhishu.gateway.quota.GatewayQuotaMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 额度策略管理实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayQuotaAdminServiceImpl implements GatewayQuotaAdminService {

    private static final String SUBJECT_DEFAULT = "DEFAULT";

    private final GatewayQuotaMapper gatewayQuotaMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GatewayQuotaVO> list(String subjectType) {
        LambdaQueryWrapper<GatewayQuotaEntity> wrapper = new LambdaQueryWrapper<GatewayQuotaEntity>()
                .orderByAsc(GatewayQuotaEntity::getSubjectType)
                .orderByAsc(GatewayQuotaEntity::getSubjectKey);
        if (StringUtils.hasText(subjectType)) {
            wrapper.eq(GatewayQuotaEntity::getSubjectType, subjectType.trim().toUpperCase());
        }
        List<GatewayQuotaEntity> rows = gatewayQuotaMapper.selectList(wrapper);
        List<GatewayQuotaVO> result = new ArrayList<>(rows.size());
        for (GatewayQuotaEntity row : rows) {
            result.add(toVO(row));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GatewayQuotaVO create(GatewayQuotaUpsertDTO dto) {
        String subjectType = dto.normalizedSubjectType();
        String subjectKey = dto.normalizedSubjectKey();
        GatewayQuotaEntity existing = findBySubject(subjectType, subjectKey);
        if (existing != null) {
            throw new BusinessException(400, "该主体的额度策略已存在: " + subjectType + ":" + subjectKey);
        }
        LocalDateTime periodStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        GatewayQuotaEntity entity = new GatewayQuotaEntity();
        entity.setSubjectType(subjectType);
        entity.setSubjectKey(subjectKey);
        entity.setPeriodKind(periodKind(dto));
        entity.setTokenLimit(dto.tokenLimit());
        entity.setUsedTokens(0L);
        entity.setPeriodStart(periodStart);
        entity.setPeriodEnd(periodStart.plusMonths(1));
        entity.setStatus(status(dto));
        entity.setRemark(dto.remark());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        gatewayQuotaMapper.insert(entity);
        log.info("[网关管理] 新建额度策略 {}:{} limit={}", subjectType, subjectKey, dto.tokenLimit());
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GatewayQuotaVO update(Long id, GatewayQuotaUpsertDTO dto) {
        GatewayQuotaEntity entity = requireEntity(id);
        if (!entity.getSubjectType().equalsIgnoreCase(dto.normalizedSubjectType())
                || !entity.getSubjectKey().equals(dto.normalizedSubjectKey())) {
            throw new BusinessException(400, "额度主体不可变更，请删除后重建");
        }
        entity.setPeriodKind(periodKind(dto));
        entity.setTokenLimit(dto.tokenLimit());
        entity.setStatus(status(dto));
        entity.setRemark(dto.remark());
        entity.setUpdateTime(LocalDateTime.now());
        gatewayQuotaMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        GatewayQuotaEntity entity = requireEntity(id);
        if (SUBJECT_DEFAULT.equalsIgnoreCase(entity.getSubjectType())) {
            throw new BusinessException(400, "DEFAULT 兜底额度不可删除，请修改其上限");
        }
        gatewayQuotaMapper.deleteById(id);
        log.info("[网关管理] 删除额度策略 id={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GatewayQuotaVO reset(Long id) {
        GatewayQuotaEntity entity = requireEntity(id);
        entity.setUsedTokens(0L);
        entity.setUpdateTime(LocalDateTime.now());
        gatewayQuotaMapper.updateById(entity);
        log.info("[网关管理] 清零额度用量 id={}", id);
        return toVO(entity);
    }

    private GatewayQuotaEntity requireEntity(Long id) {
        GatewayQuotaEntity entity = gatewayQuotaMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "额度策略不存在: " + id);
        }
        return entity;
    }

    private GatewayQuotaEntity findBySubject(String subjectType, String subjectKey) {
        return gatewayQuotaMapper.selectOne(new LambdaQueryWrapper<GatewayQuotaEntity>()
                .eq(GatewayQuotaEntity::getSubjectType, subjectType)
                .eq(GatewayQuotaEntity::getSubjectKey, subjectKey)
                .last("LIMIT 1"));
    }

    private static String periodKind(GatewayQuotaUpsertDTO dto) {
        return StringUtils.hasText(dto.periodKind()) ? dto.periodKind().trim().toUpperCase() : "MONTHLY";
    }

    private static String status(GatewayQuotaUpsertDTO dto) {
        return StringUtils.hasText(dto.status()) ? dto.status().trim().toUpperCase() : "ENABLED";
    }

    private GatewayQuotaVO toVO(GatewayQuotaEntity entity) {
        Long limit = entity.getTokenLimit();
        long used = entity.getUsedTokens() == null ? 0L : entity.getUsedTokens();
        long remaining = limit == null ? -1L : Math.max(limit - used, 0L);
        return new GatewayQuotaVO(
                entity.getId(),
                entity.getSubjectType(),
                entity.getSubjectKey(),
                entity.getPeriodKind(),
                limit,
                used,
                remaining,
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getStatus(),
                entity.getRemark(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
