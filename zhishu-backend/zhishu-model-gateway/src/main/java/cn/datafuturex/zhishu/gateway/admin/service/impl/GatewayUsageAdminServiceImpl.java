package cn.datafuturex.zhishu.gateway.admin.service.impl;

import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.gateway.admin.service.GatewayUsageAdminService;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayUsageSummaryVO;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayUsageSummaryVO.Bucket;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayUsageVO;
import cn.datafuturex.zhishu.gateway.usage.GatewayUsageEntity;
import cn.datafuturex.zhishu.gateway.usage.GatewayUsageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 调用台账查询实现：分页走 MyBatis-Plus，聚合走 JdbcTemplate。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayUsageAdminServiceImpl implements GatewayUsageAdminService {

    private static final int MAX_PAGE_SIZE = 200;

    private final GatewayUsageMapper gatewayUsageMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<GatewayUsageVO> page(String username, String modelId, String channel, String status,
                                           LocalDateTime from, LocalDateTime to, long current, long size) {
        LambdaQueryWrapper<GatewayUsageEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(GatewayUsageEntity::getUsername, username.trim());
        }
        if (StringUtils.hasText(modelId)) {
            wrapper.eq(GatewayUsageEntity::getModelId, modelId.trim());
        }
        if (StringUtils.hasText(channel)) {
            wrapper.eq(GatewayUsageEntity::getChannel, channel.trim().toUpperCase());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(GatewayUsageEntity::getStatus, status.trim().toUpperCase());
        }
        if (from != null) {
            wrapper.ge(GatewayUsageEntity::getCreateTime, from);
        }
        if (to != null) {
            wrapper.le(GatewayUsageEntity::getCreateTime, to);
        }
        wrapper.orderByDesc(GatewayUsageEntity::getId);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        Page<GatewayUsageEntity> page = gatewayUsageMapper.selectPage(
                new Page<>(Math.max(current, 1L), pageSize), wrapper);

        List<GatewayUsageVO> records = new ArrayList<>(page.getRecords().size());
        for (GatewayUsageEntity entity : page.getRecords()) {
            records.add(toVO(entity));
        }
        Page<GatewayUsageVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records);
        return PageResult.of(voPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GatewayUsageSummaryVO summary(LocalDateTime from, LocalDateTime to) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (from != null) {
            where.append(" AND create_time >= ?");
            args.add(from);
        }
        if (to != null) {
            where.append(" AND create_time <= ?");
            args.add(to);
        }

        Map<String, Object> totals = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) AS requests,
                       COALESCE(SUM(total_tokens), 0) AS tokens,
                       COUNT(*) FILTER (WHERE status = 'SUCCESS') AS success_count,
                       COUNT(*) FILTER (WHERE status <> 'SUCCESS') AS error_count,
                       COUNT(*) FILTER (WHERE estimated = TRUE) AS estimated_count
                  FROM ai_gateway_usage
                """ + where, args.toArray());

        return new GatewayUsageSummaryVO(
                toLong(totals.get("requests")),
                toLong(totals.get("tokens")),
                toLong(totals.get("success_count")),
                toLong(totals.get("error_count")),
                toLong(totals.get("estimated_count")),
                buckets("model_id", where.toString(), args, "LIMIT 20"),
                buckets("username", where.toString(), args, "LIMIT 20"),
                buckets("to_char(create_time, 'YYYY-MM-DD')", where.toString(), args, "ORDER BY 1"));
    }

    private List<Bucket> buckets(String keyExpression, String where, List<Object> args, String tail) {
        String order = tail.startsWith("ORDER") ? tail : "ORDER BY tokens DESC " + tail;
        String sql = """
                SELECT %s AS bucket_key,
                       COUNT(*) AS requests,
                       COALESCE(SUM(total_tokens), 0) AS tokens
                  FROM ai_gateway_usage
                %s
                 GROUP BY 1
                %s
                """.formatted(keyExpression, where, order);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        List<Bucket> buckets = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object key = row.get("bucket_key");
            buckets.add(new Bucket(key == null ? "(未知)" : key.toString(),
                    toLong(row.get("requests")), toLong(row.get("tokens"))));
        }
        return buckets;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private GatewayUsageVO toVO(GatewayUsageEntity entity) {
        return new GatewayUsageVO(
                entity.getId(),
                entity.getRequestId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getChannel(),
                entity.getModelId(),
                entity.getProviderId(),
                entity.getUpstreamModel(),
                entity.getStream(),
                entity.getStatus(),
                entity.getHttpStatus(),
                entity.getReservedTokens(),
                entity.getPromptTokens(),
                entity.getCompletionTokens(),
                entity.getTotalTokens(),
                entity.getCachedTokens(),
                entity.getReasoningTokens(),
                entity.getEstimated(),
                entity.getLatencyMs(),
                entity.getTtftMs(),
                entity.getClientName(),
                entity.getClientVersion(),
                entity.getIp(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getCreateTime());
    }
}
