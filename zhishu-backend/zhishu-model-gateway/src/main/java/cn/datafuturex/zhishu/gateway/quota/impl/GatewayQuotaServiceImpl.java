package cn.datafuturex.zhishu.gateway.quota.impl;

import cn.datafuturex.zhishu.gateway.auth.GatewayPrincipal;
import cn.datafuturex.zhishu.gateway.compat.GatewayException;
import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import cn.datafuturex.zhishu.gateway.quota.GatewayQuotaEntity;
import cn.datafuturex.zhishu.gateway.quota.GatewayQuotaMapper;
import cn.datafuturex.zhishu.gateway.quota.GatewayQuotaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 额度实现：USER → ROLE → DEFAULT 解析，请求前原子预留、结束后按差额结算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayQuotaServiceImpl implements GatewayQuotaService {

    private static final String SUBJECT_USER = "USER";
    private static final String SUBJECT_ROLE = "ROLE";
    private static final String SUBJECT_DEFAULT = "DEFAULT";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final DateTimeFormatter RESET_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final GatewayQuotaMapper gatewayQuotaMapper;
    private final GatewayProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public QuotaReservation reserve(GatewayPrincipal principal, long requestTokens) {
        if (!properties.getQuota().isEnabled()) {
            return new QuotaReservation(null, 0L, null, null);
        }
        GatewayQuotaEntity row = rollIfNeeded(ensureUserQuota(principal));
        Long limit = row.getTokenLimit();
        Long resetAt = toEpochSecond(row.getPeriodEnd());
        if (limit != null) {
            long used = row.getUsedTokens() == null ? 0L : row.getUsedTokens();
            long remaining = limit - used;
            if (remaining < requestTokens) {
                throw quotaExceeded(limit, used, row.getPeriodEnd());
            }
        }
        int updated = gatewayQuotaMapper.addUsage(row.getId(), requestTokens);
        if (updated == 0) {
            GatewayQuotaEntity latest = gatewayQuotaMapper.selectById(row.getId());
            throw quotaExceeded(
                    latest != null ? latest.getTokenLimit() : limit,
                    latest != null && latest.getUsedTokens() != null ? latest.getUsedTokens() : 0L,
                    latest != null ? latest.getPeriodEnd() : row.getPeriodEnd());
        }
        return new QuotaReservation(row.getId(), requestTokens, limit, resetAt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void settle(Long quotaId, long delta) {
        if (quotaId == null || delta == 0L) {
            return;
        }
        gatewayQuotaMapper.settle(quotaId, delta);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void release(Long quotaId, long reservedTokens) {
        settle(quotaId, -reservedTokens);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public QuotaSnapshot snapshot(GatewayPrincipal principal) {
        GatewayQuotaEntity row = rollIfNeeded(ensureUserQuota(principal));
        long used = row.getUsedTokens() == null ? 0L : row.getUsedTokens();
        Long limit = row.getTokenLimit();
        long remaining = limit == null ? -1L : Math.max(limit - used, 0L);
        return new QuotaSnapshot(row.getSubjectType(), row.getSubjectKey(),
                row.getPeriodKind() == null ? "MONTHLY" : row.getPeriodKind(),
                limit, used, remaining, toEpochSecond(row.getPeriodEnd()), row.getStatus());
    }

    private GatewayQuotaEntity ensureUserQuota(GatewayPrincipal principal) {
        GatewayQuotaEntity userRow = findBySubject(SUBJECT_USER, principal.username());
        if (userRow != null) {
            return userRow;
        }
        GatewayQuotaEntity template = findBySubject(SUBJECT_ROLE, principal.role());
        if (template == null) {
            template = findBySubject(SUBJECT_DEFAULT, "*");
        }
        Long limit = template != null && template.getTokenLimit() != null
                ? template.getTokenLimit()
                : properties.getQuota().getDefaultMonthlyTokens();
        LocalDateTime periodStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        GatewayQuotaEntity created = new GatewayQuotaEntity();
        created.setSubjectType(SUBJECT_USER);
        created.setSubjectKey(principal.username());
        created.setPeriodKind("MONTHLY");
        created.setTokenLimit(limit);
        created.setUsedTokens(0L);
        created.setPeriodStart(periodStart);
        created.setPeriodEnd(periodStart.plusMonths(1));
        created.setStatus(STATUS_ENABLED);
        created.setRemark("首次调用自动创建");
        created.setCreateTime(LocalDateTime.now());
        created.setUpdateTime(LocalDateTime.now());
        try {
            gatewayQuotaMapper.insert(created);
            log.info("[网关额度] 首次调用创建用户额度 username={}, limit={}", principal.username(), limit);
            return created;
        } catch (DuplicateKeyException e) {
            GatewayQuotaEntity existing = findBySubject(SUBJECT_USER, principal.username());
            if (existing != null) {
                return existing;
            }
            throw e;
        }
    }

    private GatewayQuotaEntity rollIfNeeded(GatewayQuotaEntity row) {
        if (row.getPeriodEnd() != null && row.getPeriodEnd().isAfter(LocalDateTime.now())) {
            return row;
        }
        gatewayQuotaMapper.rollPeriod(row.getId());
        GatewayQuotaEntity refreshed = gatewayQuotaMapper.selectById(row.getId());
        return refreshed != null ? refreshed : row;
    }

    private GatewayQuotaEntity findBySubject(String subjectType, String subjectKey) {
        if (!StringUtils.hasText(subjectKey)) {
            return null;
        }
        return gatewayQuotaMapper.selectOne(new LambdaQueryWrapper<GatewayQuotaEntity>()
                .eq(GatewayQuotaEntity::getSubjectType, subjectType)
                .eq(GatewayQuotaEntity::getSubjectKey, subjectKey)
                .eq(GatewayQuotaEntity::getStatus, STATUS_ENABLED)
                .last("LIMIT 1"));
    }

    private GatewayException quotaExceeded(Long limit, long used, LocalDateTime periodEnd) {
        String resetHint = periodEnd == null ? "" : "，" + RESET_FORMAT.format(periodEnd) + " 重置";
        return GatewayException.insufficientQuota(
                "本月 token 额度已用尽" + resetHint,
                toEpochSecond(periodEnd),
                limit,
                used);
    }

    private static Long toEpochSecond(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }
}
