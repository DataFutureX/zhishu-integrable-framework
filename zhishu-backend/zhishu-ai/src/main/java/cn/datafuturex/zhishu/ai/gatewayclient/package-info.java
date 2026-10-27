/**
 * 模型接入层：只提供“经网关调用”的客户端与回退解析，不再持有跨模块端口。
 * <p>
 * 推理目标端口、通道常量与 OpenAI 兼容构建器已下沉到模型网关的导出面
 * （{@code gateway.port} / {@code gateway.upstream}）；{@code config} 仅保留内部调用参数，
 * 根包为 ChatModel 注册表与目录/令牌提供器。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "GatewayClient",
        allowedDependencies = {"shared"}
)
package cn.datafuturex.zhishu.ai.gatewayclient;
