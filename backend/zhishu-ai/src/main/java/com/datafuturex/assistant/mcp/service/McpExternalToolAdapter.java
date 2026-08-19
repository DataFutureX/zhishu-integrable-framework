package com.datafuturex.assistant.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafuturex.assistant.mcp.client.McpUpstreamConnectionManager;
import com.datafuturex.assistant.mcp.config.McpProperties;
import com.datafuturex.assistant.mcp.domain.entity.AiAgentMcpUpstreamEntity;
import com.datafuturex.assistant.mcp.domain.entity.AiMcpUpstreamEntity;
import com.datafuturex.assistant.mcp.domain.entity.AiMcpUpstreamToolEntity;
import com.datafuturex.assistant.mcp.mapper.AiAgentMcpUpstreamMapper;
import com.datafuturex.assistant.mcp.mapper.AiMcpUpstreamMapper;
import com.datafuturex.assistant.mcp.mapper.AiMcpUpstreamToolMapper;
import com.datafuturex.assistant.mcp.support.InboundAuditingToolCallback;
import com.datafuturex.assistant.mcp.support.McpJson;
import com.datafuturex.assistant.mcp.support.PrefixedToolCallback;
import com.datafuturex.assistant.shared.exception.BusinessException;
import com.datafuturex.assistant.shared.mcp.ExternalToolPort;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class McpExternalToolAdapter implements ExternalToolPort {

    private final AiAgentMcpUpstreamMapper bindMapper;
    private final AiMcpUpstreamMapper upstreamMapper;
    private final AiMcpUpstreamToolMapper toolMapper;
    private final McpUpstreamConnectionManager connectionManager;
    private final McpCallLogService callLogService;
    private final McpProperties properties;

    @Override
    public List<ToolCallback> resolveForAgent(Long agentId) {
        if (agentId == null || !properties.isClientEnabled() || !properties.isEnabled()) {
            return List.of();
        }
        List<AiAgentMcpUpstreamEntity> binds = bindMapper.selectList(
                new LambdaQueryWrapper<AiAgentMcpUpstreamEntity>()
                        .eq(AiAgentMcpUpstreamEntity::getAgentId, agentId));
        List<ToolCallback> result = new ArrayList<>();
        for (AiAgentMcpUpstreamEntity bind : binds) {
            AiMcpUpstreamEntity upstream = upstreamMapper.selectById(bind.getUpstreamId());
            if (upstream == null || !"ENABLED".equalsIgnoreCase(upstream.getStatus())) {
                continue;
            }
            Map<String, AiMcpUpstreamToolEntity> cache = toolMapper.selectList(
                            new LambdaQueryWrapper<AiMcpUpstreamToolEntity>()
                                    .eq(AiMcpUpstreamToolEntity::getUpstreamId, upstream.getId()))
                    .stream()
                    .collect(Collectors.toMap(AiMcpUpstreamToolEntity::getOriginalName, Function.identity(), (a, b) -> a));
            Set<String> allow = new LinkedHashSet<>(McpJson.parseStringList(bind.getAllowedTools()));
            List<ToolCallback> remote = connectionManager.callbacks(upstream.getId());
            for (ToolCallback cb : remote) {
                String original = cb.getToolDefinition().name();
                AiMcpUpstreamToolEntity meta = cache.get(original);
                if (meta != null && Boolean.FALSE.equals(meta.getEnabled())) {
                    continue;
                }
                String exposed = meta != null && StringUtils.hasText(meta.getExposedName())
                        ? meta.getExposedName()
                        : McpUpstreamConnectionManager.exposedName(upstream.getCode(), original);
                if (!allow.isEmpty() && !allow.contains(original) && !allow.contains(exposed)) {
                    continue;
                }
                ToolCallback prefixed = new PrefixedToolCallback(
                        cb, exposed, "[MCP:" + upstream.getCode() + "] ");
                result.add(new InboundAuditingToolCallback(prefixed, callLogService, upstream.getId(), agentId));
            }
        }
        return result;
    }

    @Override
    public List<Long> listBoundUpstreamIds(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        return bindMapper.selectList(new LambdaQueryWrapper<AiAgentMcpUpstreamEntity>()
                        .eq(AiAgentMcpUpstreamEntity::getAgentId, agentId)
                        .orderByAsc(AiAgentMcpUpstreamEntity::getId))
                .stream()
                .map(AiAgentMcpUpstreamEntity::getUpstreamId)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional
    public void bindAgentUpstreams(Long agentId, List<Long> upstreamIds) {
        if (agentId == null) {
            return;
        }
        List<Long> ids = upstreamIds == null ? List.of() : upstreamIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.size() > properties.getMaxUpstreamsPerAgent()) {
            throw new BusinessException("每个智能体最多绑定 " + properties.getMaxUpstreamsPerAgent() + " 个上游 MCP");
        }
        bindMapper.delete(new LambdaQueryWrapper<AiAgentMcpUpstreamEntity>()
                .eq(AiAgentMcpUpstreamEntity::getAgentId, agentId));
        LocalDateTime now = LocalDateTime.now();
        for (Long upstreamId : ids) {
            AiMcpUpstreamEntity upstream = upstreamMapper.selectById(upstreamId);
            if (upstream == null) {
                throw new BusinessException("上游 MCP 不存在: " + upstreamId);
            }
            AiAgentMcpUpstreamEntity row = new AiAgentMcpUpstreamEntity();
            row.setAgentId(agentId);
            row.setUpstreamId(upstreamId);
            row.setCreateTime(now);
            bindMapper.insert(row);
        }
    }
}
