package cn.datafuturex.zhishu.ai.gatewayclient;

import cn.datafuturex.zhishu.ai.gatewayclient.config.GatewayInternalProperties;
import cn.datafuturex.zhishu.ai.modelconfig.api.AiChatTargetPort;
import cn.datafuturex.zhishu.ai.modelconfig.api.GatewayChannels;
import cn.datafuturex.zhishu.ai.modelconfig.runtime.RefreshableOpenAiModels;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 内部 ChatModel 解析器：按网关 model_id 构建指向 {@code <网关>/v1} 的 OpenAI 兼容客户端。
 * <p>
 * {@code zhishu.gateway.internal.enabled=false} 时回退直连上游（灰度/回滚）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayChatModelRegistry {

    private final GatewayInternalProperties properties;
    private final GatewayServiceTokenProvider tokenProvider;
    private final GatewayModelCatalogClient catalogClient;
    private final ObjectProvider<AiChatTargetPort> chatTargetPort;

    /** (bearerHash + modelId) -> ChatModel */
    private final Cache<String, ChatModel> cache = Caffeine.newBuilder()
            .maximumSize(128)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    /**
     * 内部统一开关是否开启。
     *
     * @return true=经网关
     */
    public boolean enabled() {
        return properties.isEnabled();
    }

    /**
     * 解析 ChatModel（使用配置的默认内部通道）。
     *
     * @param gatewayModelId Agent 绑定的网关模型 id，空=用网关默认模型
     * @return ChatModel
     */
    public ChatModel resolve(String gatewayModelId) {
        return resolve(gatewayModelId, properties.getChannel());
    }

    /**
     * 解析 ChatModel 并声明台账通道。
     * <p>
     * 通道写入 {@code X-Zhishu-Channel} 请求头，使台账能区分 Agent 推理与控制台/桌面直连；
     * 回退直连上游时不注入该头（属于平台内部协议）。
     *
     * @param gatewayModelId 网关模型 id，空=用网关默认模型
     * @param channel        内部调用通道（如 AGENT / CONSOLE）
     * @return ChatModel
     */
    public ChatModel resolve(String gatewayModelId, String channel) {
        if (!enabled()) {
            return fallbackChatModel(gatewayModelId);
        }
        String modelId = resolveModelId(gatewayModelId);
        String bearer = bearer();
        String effectiveChannel = StringUtils.hasText(channel)
                ? channel.trim().toUpperCase() : GatewayChannels.CONSOLE;
        String cacheKey = Integer.toHexString(bearer.hashCode()) + '|' + modelId + '|' + effectiveChannel;
        return cache.get(cacheKey, key -> RefreshableOpenAiModels.chatModel(
                baseUrl(), bearer, modelId, null, null, null,
                Map.of(GatewayChannels.HEADER, effectiveChannel)));
    }

    /**
     * 回退模式：优先按模型行直连上游，取不到再用默认供应商。
     *
     * @param gatewayModelId 网关模型 id
     * @return ChatModel
     */
    private ChatModel fallbackChatModel(String gatewayModelId) {
        AiChatTargetPort port = chatTargetPort.getIfAvailable();
        if (port == null) {
            throw new AiException("未装配模型网关模块：无法解析对话模型");
        }
        if (port.enabled() && StringUtils.hasText(gatewayModelId)) {
            Optional<AiChatTargetPort.Target> target = port.resolveByModelId(gatewayModelId);
            if (target.isPresent()) {
                AiChatTargetPort.Target value = target.get();
                return cache.get("direct|" + value.modelId(), key -> RefreshableOpenAiModels.chatModel(
                        value.baseUrl(), value.apiKey(), value.upstreamModel(),
                        value.temperature(), value.maxTokens(), value.topP()));
            }
        }
        return port.defaultChatModel();
    }

    /**
     * 解析最终使用的模型 id（空值时取网关默认模型）。
     *
     * @param gatewayModelId 绑定的模型 id
     * @return 实际使用的模型 id
     */
    public String resolveModelId(String gatewayModelId) {
        if (!enabled()) {
            return gatewayModelId;
        }
        if (StringUtils.hasText(gatewayModelId)) {
            if (!catalogClient.contains(gatewayModelId)) {
                log.warn("[网关客户端] 模型 {} 不在网关目录中，将由网关裁决", gatewayModelId);
            }
            return gatewayModelId;
        }
        return catalogClient.defaultModelId()
                .orElseThrow(() -> new AiException("未配置模型网关默认模型，请先在「模型网关 · 网关模型」中创建目录并设为默认"));
    }

    /**
     * 描述本次使用的模型（日志/监控用，不抛异常）。
     *
     * @param gatewayModelId 绑定的网关模型 id
     * @return 模型名；无法解析时返回原值或 null
     */
    public String describeModel(String gatewayModelId) {
        if (!enabled()) {
            AiChatTargetPort port = chatTargetPort.getIfAvailable();
            if (port != null) {
                String name = port.describeDefaultChatModelName();
                if (StringUtils.hasText(name)) {
                    return name;
                }
            }
            return gatewayModelId;
        }
        try {
            return resolveModelId(gatewayModelId);
        } catch (Exception e) {
            log.debug("[网关客户端] 模型名解析失败，回退原值: {}", e.getMessage());
            return gatewayModelId;
        }
    }

    /**
     * 展示/审计用：当前会实际使用的默认模型名（网关 model_id）。
     *
     * @return 模型名；无法解析时返回 null（不抛异常，不影响业务写入）
     */
    public String defaultModelLabel() {
        try {
            return describeModel(null);
        } catch (Exception e) {
            log.debug("[网关客户端] 默认模型名解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 当前调用应使用的 bearer：默认透传当前登录用户 token，无上下文时用服务账号。
     *
     * @return bearer token
     */
    public String bearer() {
        if ("platform".equalsIgnoreCase(properties.getQuotaSubject())) {
            return tokenProvider.token();
        }
        String userToken = UserContext.getToken();
        return StringUtils.hasText(userToken) ? userToken : tokenProvider.token();
    }

    /**
     * 网关 OpenAI 兼容 baseUrl。
     *
     * @return 形如 {@code http://127.0.0.1:8180/v1}
     */
    public String baseUrl() {
        String base = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/v1";
    }

    /**
     * 网关推理端点（日志用）。
     *
     * @return 完整端点地址
     */
    public String completionsEndpoint() {
        return baseUrl() + "/chat/completions";
    }
}
