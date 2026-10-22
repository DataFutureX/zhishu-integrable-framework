/**
 * 模型网关内部客户端：把平台内部（Agent / 问答 / 简报）的推理统一转发到网关。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "GatewayClient",
        allowedDependencies = {"shared", "modelconfig"}
)
package cn.datafuturex.zhishu.ai.gatewayclient;
