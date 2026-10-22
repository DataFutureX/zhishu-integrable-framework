package cn.datafuturex.zhishu.gateway.usage.impl;

import cn.datafuturex.zhishu.gateway.usage.GatewayUsageEntity;
import cn.datafuturex.zhishu.gateway.usage.GatewayUsageMapper;
import cn.datafuturex.zhishu.gateway.usage.GatewayUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 台账实现：begin 落 RESERVED 行，finish 按结果收敛（异常只记日志，不影响响应）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayUsageServiceImpl implements GatewayUsageService {

    private static final String STATUS_RESERVED = "RESERVED";

    private final GatewayUsageMapper gatewayUsageMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public long begin(BeginCommand command) {
        GatewayUsageEntity entity = new GatewayUsageEntity();
        entity.setRequestId(command.requestId());
        entity.setUserId(command.principal().username());
        entity.setUsername(command.principal().username());
        entity.setChannel(command.channel());
        entity.setModelId(command.view().modelId());
        entity.setProviderId(command.view().providerId());
        entity.setUpstreamModel(command.view().upstreamModel());
        entity.setStream(command.stream());
        entity.setStatus(STATUS_RESERVED);
        entity.setReservedTokens(command.reservedTokens());
        entity.setEstimated(Boolean.FALSE);
        if (command.meta() != null) {
            entity.setIp(command.meta().ip());
            entity.setUserAgent(truncate(command.meta().userAgent(), 255));
            entity.setClientName(truncate(command.meta().clientName(), 32));
            entity.setClientVersion(truncate(command.meta().clientVersion(), 32));
        }
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        gatewayUsageMapper.insert(entity);
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finish(long usageId, Outcome outcome) {
        try {
            GatewayUsageEntity entity = new GatewayUsageEntity();
            entity.setId(usageId);
            entity.setStatus(outcome.status());
            entity.setHttpStatus(outcome.httpStatus());
            entity.setPromptTokens(outcome.promptTokens());
            entity.setCompletionTokens(outcome.completionTokens());
            entity.setTotalTokens(outcome.totalTokens());
            entity.setCachedTokens(outcome.cachedTokens());
            entity.setReasoningTokens(outcome.reasoningTokens());
            entity.setEstimated(outcome.estimated());
            entity.setLatencyMs(toInt(outcome.latencyMs()));
            entity.setTtftMs(toInt(outcome.ttftMs()));
            entity.setErrorCode(truncate(outcome.errorCode(), 64));
            entity.setErrorMessage(truncate(outcome.errorMessage(), 500));
            entity.setUpdateTime(LocalDateTime.now());
            gatewayUsageMapper.updateById(entity);
        } catch (Exception e) {
            log.warn("[网关台账] 结算写入失败 usageId={}: {}", usageId, e.getMessage());
        }
    }

    private static Integer toInt(Long value) {
        if (value == null) {
            return null;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
