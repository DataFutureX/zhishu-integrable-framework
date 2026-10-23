/**
 * 模型接入层（原 modelconfig 已合并进来）：对内提供推理目标端口与经网关调用的客户端。
 * <p>
 * 子包职责：{@code api} 跨模块端口与通道常量，{@code runtime} OpenAI 兼容构建器，
 * {@code config} 运行时参数，根包为经网关调用的 ChatModel 客户端与目录/令牌提供器。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "GatewayClient",
        allowedDependencies = {"shared"}
)
package cn.datafuturex.zhishu.ai.gatewayclient;
