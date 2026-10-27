/**
 * Web / OpenAPI / Spring AI 装配（开放模块）。
 * <p>
 * 网关相关依赖只允许出现在它的导出面 {@code gateway.port} / {@code gateway.upstream}。
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Platform",
        allowedDependencies = {"biztools :: api", "shared"}
)
package cn.datafuturex.zhishu.ai.platform;
