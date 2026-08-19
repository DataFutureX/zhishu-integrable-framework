package com.datafuturex.assistant.agent.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Alibaba Agent Framework 适配占位：当前与 Spring AI 2.0 GA 不兼容时不可用。
 */
@Component
@Slf4j
public class AlibabaAgentEngine implements AgentEngine {

    public static final String NAME = "alibaba";

    private final boolean compatible;

    public AlibabaAgentEngine() {
        this.compatible = probeCompatible();
        if (!compatible) {
            log.warn("AlibabaAgentEngine 不可用：依赖 API 与 Spring AI 2.0 GA 不兼容，将回退 chatclient");
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean available() {
        return compatible;
    }

    @Override
    public AgentRuntimeResult execute(AgentRuntimeRequest request) {
        throw new UnsupportedOperationException(
                "AlibabaAgentEngine 当前不可用，请将 wanxiang.agent.engine 设为 chatclient");
    }

    private static boolean probeCompatible() {
        try {
            // ReactAgent / ChatClient.Builder.defaultOptions(ChatOptions) 等在 GA 上已变更
            Class.forName("com.alibaba.cloud.ai.graph.agent.ReactAgent");
            // 探测已移除的 API（存在则仍为 M1 语义，与 GA 冲突）
            try {
                Class<?> opts = Class.forName(
                        "org.springframework.ai.model.tool.ToolCallingChatOptions$Builder");
                opts.getMethod("internalToolExecutionEnabled", Boolean.class);
                // 方法仍存在 → 可能是 M1；GA 已移除该方法
                return false;
            } catch (NoSuchMethodException e) {
                // GA 无此方法；再探测 ChatClient.Builder.defaultOptions 签名是否接受 ChatOptions
                return false;
            }
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
