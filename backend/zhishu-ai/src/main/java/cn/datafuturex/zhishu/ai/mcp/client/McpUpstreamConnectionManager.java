package cn.datafuturex.zhishu.ai.mcp.client;

import cn.datafuturex.zhishu.ai.mcp.config.McpProperties;
import cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpUpstreamEntity;
import cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpUpstreamToolEntity;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpUpstreamMapper;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpUpstreamToolMapper;
import cn.datafuturex.zhishu.ai.mcp.support.McpCrypto;
import cn.datafuturex.zhishu.ai.mcp.support.McpUpstreamCircuit;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpUpstreamConnectionManager {

    private final AiMcpUpstreamMapper upstreamMapper;
    private final AiMcpUpstreamToolMapper upstreamToolMapper;
    private final McpProperties properties;
    private final McpUpstreamCircuit circuit;

    private final Map<Long, McpSyncClient> clients = new ConcurrentHashMap<>();

    public synchronized List<ToolCallback> connectAndList(AiMcpUpstreamEntity upstream) {
        closeQuietly(upstream.getId());
        try {
            McpSyncClient client = openClient(upstream);
            clients.put(upstream.getId(), client);
            ToolCallback[] raw = new SyncMcpToolCallbackProvider(client).getToolCallbacks();
            List<ToolCallback> list = raw == null ? List.of() : List.of(raw);
            persistToolCache(upstream, list);
            markHealth(upstream.getId(), "UP", "ok, tools=" + list.size());
            circuit.success(upstream.getId());
            return list;
        } catch (RuntimeException e) {
            circuit.failure(upstream.getId());
            throw e;
        }
    }

    public List<ToolCallback> callbacks(Long upstreamId) {
        if (!circuit.allow(upstreamId)) {
            log.debug("上游 MCP 熔断中 id={}", upstreamId);
            return List.of();
        }
        McpSyncClient client = clients.get(upstreamId);
        if (client == null) {
            AiMcpUpstreamEntity entity = upstreamMapper.selectById(upstreamId);
            if (entity == null || !"ENABLED".equalsIgnoreCase(entity.getStatus())) {
                return List.of();
            }
            try {
                return connectAndList(entity);
            } catch (Exception e) {
                log.warn("连接上游 MCP 失败 id={}: {}", upstreamId, e.getMessage());
                return List.of();
            }
        }
        try {
            ToolCallback[] raw = new SyncMcpToolCallbackProvider(client).getToolCallbacks();
            return raw == null ? List.of() : List.of(raw);
        } catch (RuntimeException e) {
            circuit.failure(upstreamId);
            closeQuietly(upstreamId);
            log.warn("读取上游 MCP 工具失败 id={}: {}", upstreamId, e.getMessage());
            return List.of();
        }
    }

    public synchronized void disconnect(Long upstreamId) {
        closeQuietly(upstreamId);
    }

    public void disconnectAll() {
        for (Long id : List.copyOf(clients.keySet())) {
            closeQuietly(id);
        }
    }

    private McpSyncClient openClient(AiMcpUpstreamEntity upstream) {
        rejectStdio(upstream.getProtocol());
        String auth = McpCrypto.decrypt(upstream.getAuthHeaderEnc(), properties.getCryptoKey());
        Duration timeout = Duration.ofMillis(
                upstream.getRequestTimeoutMs() == null ? 20_000 : upstream.getRequestTimeoutMs());
        String endpoint = StringUtils.hasText(upstream.getEndpoint()) ? upstream.getEndpoint() : "/mcp";
        String protocol = upstream.getProtocol() == null ? "STREAMABLE_HTTP" : upstream.getProtocol();
        try {
            if ("SSE".equalsIgnoreCase(protocol)) {
                var transport = HttpClientSseClientTransport.builder(upstream.getBaseUrl())
                        .sseEndpoint(endpoint)
                        .httpRequestCustomizer((builder, method, uri, body, ctx) -> applyAuth(builder, auth))
                        .build();
                McpSyncClient client = McpClient.sync(transport).requestTimeout(timeout).build();
                client.initialize();
                return client;
            }
            if (!"STREAMABLE_HTTP".equalsIgnoreCase(protocol) && !"HTTP".equalsIgnoreCase(protocol)) {
                throw new BusinessException("不支持的上游协议: " + protocol);
            }
            var transport = HttpClientStreamableHttpTransport.builder(upstream.getBaseUrl())
                    .endpoint(endpoint)
                    .httpRequestCustomizer((builder, method, uri, body, ctx) -> applyAuth(builder, auth))
                    .build();
            McpSyncClient client = McpClient.sync(transport).requestTimeout(timeout).build();
            client.initialize();
            return client;
        } catch (BusinessException e) {
            markHealth(upstream.getId(), "DOWN", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            String target = (upstream.getBaseUrl() == null ? "" : upstream.getBaseUrl()) + endpoint;
            String reason = explainFailure(e, target);
            markHealth(upstream.getId(), "DOWN", reason);
            throw new BusinessException("连接上游 MCP 失败: " + reason, e);
        }
    }

    private void persistToolCache(AiMcpUpstreamEntity upstream, List<ToolCallback> tools) {
        int max = Math.max(1, properties.getMaxToolsPerUpstream());
        List<ToolCallback> limited = tools.size() > max ? tools.subList(0, max) : tools;
        Map<String, Boolean> previousEnabled = new HashMap<>();
        upstreamToolMapper.selectList(new LambdaQueryWrapper<AiMcpUpstreamToolEntity>()
                        .eq(AiMcpUpstreamToolEntity::getUpstreamId, upstream.getId()))
                .forEach(row -> previousEnabled.put(row.getOriginalName(), Boolean.TRUE.equals(row.getEnabled())));
        upstreamToolMapper.delete(new LambdaQueryWrapper<AiMcpUpstreamToolEntity>()
                .eq(AiMcpUpstreamToolEntity::getUpstreamId, upstream.getId()));
        String prefix = "ext_" + sanitize(upstream.getCode()) + "_";
        LocalDateTime now = LocalDateTime.now();
        for (ToolCallback cb : limited) {
            String original = cb.getToolDefinition().name();
            AiMcpUpstreamToolEntity row = new AiMcpUpstreamToolEntity();
            row.setUpstreamId(upstream.getId());
            row.setOriginalName(original);
            row.setExposedName(prefix + original);
            row.setDescription(cb.getToolDefinition().description());
            row.setEnabled(previousEnabled.getOrDefault(original, true));
            row.setUpdateTime(now);
            upstreamToolMapper.insert(row);
        }
    }

    private void markHealth(Long id, String status, String message) {
        AiMcpUpstreamEntity patch = new AiMcpUpstreamEntity();
        patch.setId(id);
        patch.setHealthStatus(status);
        patch.setHealthMessage(truncate(message, 500));
        patch.setLastProbeAt(LocalDateTime.now());
        patch.setUpdateTime(LocalDateTime.now());
        upstreamMapper.updateById(patch);
    }

    private void closeQuietly(Long id) {
        McpSyncClient client = clients.remove(id);
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.debug("关闭上游 MCP 忽略: {}", e.getMessage());
        }
    }

    public static void rejectStdio(String protocol) {
        if (!StringUtils.hasText(protocol)) {
            return;
        }
        String normalized = protocol.trim().toUpperCase(Locale.ROOT);
        if ("STDIO".equals(normalized) || "COMMAND".equals(normalized)) {
            throw new BusinessException("生产禁止 stdio/command 型上游");
        }
    }

    private static void applyAuth(HttpRequest.Builder builder, String auth) {
        if (StringUtils.hasText(auth)) {
            builder.header("Authorization", auth.startsWith("Bearer ") ? auth : "Bearer " + auth);
        }
        String username = UserContext.getUsername();
        if (StringUtils.hasText(username)) {
            builder.header("X-On-Behalf-Of", username);
        }
    }

    public static String sanitize(String code) {
        if (!StringUtils.hasText(code)) {
            return "up";
        }
        return code.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    public static String exposedName(String code, String original) {
        return "ext_" + sanitize(code) + "_" + original;
    }

    private static String explainFailure(Throwable error, String target) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof java.net.ConnectException
                    || (cursor.getMessage() != null && cursor.getMessage().contains("Connection refused"))) {
                return "无法连接 " + target + "（对端未启动）";
            }
            cursor = cursor.getCause();
        }
        String raw = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (raw.contains("failed to initialize")) {
            return "握手失败 " + target + "（服务未启动、路径/协议不匹配或鉴权失败）";
        }
        return raw + " @ " + target;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
