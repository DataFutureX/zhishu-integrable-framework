package cn.datafuturex.zhishu.mcp.consume.runtime;

import cn.datafuturex.zhishu.mcp.common.audit.McpCallLogService;
import cn.datafuturex.zhishu.mcp.common.config.McpProperties;
import cn.datafuturex.zhishu.mcp.common.json.McpJson;
import cn.datafuturex.zhishu.mcp.consume.api.McpAgentToolPort;
import cn.datafuturex.zhishu.mcp.consume.client.McpUpstreamConnectionManager;
import cn.datafuturex.zhishu.mcp.consume.tool.PrefixedToolCallback;
import cn.datafuturex.zhishu.mcp.consume.tool.UpstreamAuditingToolCallback;
import cn.datafuturex.zhishu.mcp.consume.upstream.entity.AiAgentMcpUpstreamEntity;
import cn.datafuturex.zhishu.mcp.consume.upstream.entity.AiMcpUpstreamEntity;
import cn.datafuturex.zhishu.mcp.consume.upstream.entity.AiMcpUpstreamToolEntity;
import cn.datafuturex.zhishu.mcp.consume.upstream.mapper.AiAgentMcpUpstreamMapper;
import cn.datafuturex.zhishu.mcp.consume.upstream.mapper.AiMcpUpstreamMapper;
import cn.datafuturex.zhishu.mcp.consume.upstream.mapper.AiMcpUpstreamToolMapper;
import cn.datafuturex.zhishu.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

/**
 * consume 平面的运行时装配：把「智能体绑定的他方 MCP 工具」解析成可执行的 {@link ToolCallback}。
 * <p>
 * 解析链路：绑定关系 → 上游启停 → 工具缓存启停 → Agent 白名单 → 改名（{@code ext_<code>_<原名>}）
 * → 审计包装。任一上游不可用时只跳过该上游，不影响其余工具与本地 Tool。
 */
@Service
@RequiredArgsConstructor
public class McpAgentToolAdapter implements McpAgentToolPort {

    private final AiAgentMcpUpstreamMapper bindMapper;
    private final AiMcpUpstreamMapper upstreamMapper;
    private final AiMcpUpstreamToolMapper toolMapper;
    private final McpUpstreamConnectionManager connectionManager;
    private final McpCallLogService callLogService;
    private final McpProperties properties;

    @Override
    public List<ToolCallback> resolveForAgent(Long agentId) {
        if (agentId == null || !properties.consumeEnabled()) {
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
            for (ToolCallback callback : remote) {
                String original = callback.getToolDefinition().name();
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
                        callback, exposed, "[MCP:" + upstream.getCode() + "] ");
                result.add(new UpstreamAuditingToolCallback(prefixed, callLogService, upstream.getId(), agentId));
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
        int max = properties.getConsume().getMaxUpstreamsPerAgent();
        if (max > 0 && ids.size() > max) {
            throw new BusinessException("每个智能体最多绑定 " + max + " 个上游 MCP");
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
