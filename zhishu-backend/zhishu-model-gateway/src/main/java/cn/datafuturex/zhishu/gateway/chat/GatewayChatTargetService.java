package cn.datafuturex.zhishu.gateway.chat;

import cn.datafuturex.zhishu.ai.gatewayclient.api.AiChatTargetPort;
import cn.datafuturex.zhishu.gateway.provider.api.ModelUpstreamPort;
import cn.datafuturex.zhishu.ai.gatewayclient.runtime.RefreshableOpenAiModels;
import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelEntity;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelMapper;
import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import cn.datafuturex.zhishu.gateway.provider.runtime.ModelRuntimeCache;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 对话目标实现：模型名与生成参数一律取 {@code ai_gateway_model} 的 CHAT 模型行。
 * <p>
 * 供回退模式（{@code zhishu.gateway.internal.enabled=false}）按模型行直连上游，
 * 以及无登录上下文时提供默认 ChatModel（懒解析 + 指纹缓存）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayChatTargetService implements AiChatTargetPort {

    private static final String MODEL_TYPE_CHAT = "CHAT";

    private static final String STATUS_ENABLED = "ENABLED";

    private final GatewayModelMapper gatewayModelMapper;

    private final ModelUpstreamPort modelUpstreamPort;

    private final GatewayProperties properties;

    private final ModelRuntimeCache runtimeCache;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Target> resolveByModelId(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return Optional.empty();
        }
        return toTarget(findChatModel(modelId.trim(), false));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatModel defaultChatModel() {
        return runtimeCache.lazyChatModel(this::requireDefaultChatModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String describeDefaultChatModelName() {
        return defaultTarget().map(Target::modelId).orElse(null);
    }

    /**
     * 按默认 CHAT 模型行构建直连 ChatModel（首次解析时调用）。
     *
     * @return ChatModel
     */
    private ChatModel requireDefaultChatModel() {
        return defaultTarget()
                .map(this::directChatModel)
                .orElseThrow(() -> new AiException(
                        "未配置默认对话模型：请在「模型网关 · 模型设置」中添加 CHAT 模型并设为默认"));
    }

    /**
     * 默认 CHAT 模型目标（启用的默认行；无默认时取启用列表的首行）。
     *
     * @return 目标；无可用模型时 empty
     */
    private Optional<Target> defaultTarget() {
        GatewayModelEntity entity = findChatModel(null, true);
        if (entity == null) {
            entity = findFirstEnabled();
        }
        return toTarget(entity);
    }

    private GatewayModelEntity findChatModel(String modelId, boolean defaultOnly) {
        LambdaQueryWrapper<GatewayModelEntity> wrapper = new LambdaQueryWrapper<GatewayModelEntity>()
                .eq(GatewayModelEntity::getModelType, MODEL_TYPE_CHAT)
                .eq(GatewayModelEntity::getStatus, STATUS_ENABLED);
        if (defaultOnly) {
            wrapper.eq(GatewayModelEntity::getIsDefault, true);
        } else {
            wrapper.eq(GatewayModelEntity::getModelId, modelId);
        }
        wrapper.orderByAsc(GatewayModelEntity::getSortOrder, GatewayModelEntity::getId).last("LIMIT 1");
        return gatewayModelMapper.selectOne(wrapper);
    }

    private GatewayModelEntity findFirstEnabled() {
        return gatewayModelMapper.selectOne(new LambdaQueryWrapper<GatewayModelEntity>()
                .eq(GatewayModelEntity::getModelType, MODEL_TYPE_CHAT)
                .eq(GatewayModelEntity::getStatus, STATUS_ENABLED)
                .orderByAsc(GatewayModelEntity::getSortOrder, GatewayModelEntity::getId)
                .last("LIMIT 1"));
    }

    private Optional<Target> toTarget(GatewayModelEntity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return modelUpstreamPort.resolve(entity.getProviderId())
                .filter(ModelUpstreamPort.UpstreamEndpoint::usable)
                .map(upstream -> new Target(
                        entity.getModelId(),
                        entity.getUpstreamModel(),
                        upstream.baseUrl(),
                        upstream.apiKey(),
                        toDouble(entity.getTemperature()),
                        toDouble(entity.getTopP()),
                        entity.getMaxTokens()));
    }

    /**
     * 按目标构建（并缓存）直连上游的 ChatModel。
     *
     * @param target 对话目标
     * @return ChatModel
     */
    private ChatModel directChatModel(Target target) {
        String fingerprint = String.join("|",
                String.valueOf(target.modelId()),
                String.valueOf(target.baseUrl()),
                String.valueOf(target.upstreamModel()),
                String.valueOf(target.temperature()),
                String.valueOf(target.topP()),
                String.valueOf(target.maxTokens()),
                String.valueOf(target.apiKey() == null ? 0 : target.apiKey().hashCode()));
        return runtimeCache.chatModel(fingerprint, () -> RefreshableOpenAiModels.chatModel(
                target.baseUrl(), target.apiKey(), target.upstreamModel(),
                target.temperature(), target.maxTokens(), target.topP()));
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
