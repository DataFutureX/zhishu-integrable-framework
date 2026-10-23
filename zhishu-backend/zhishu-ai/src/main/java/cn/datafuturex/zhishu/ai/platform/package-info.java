/**
 * Web / OpenAPI / Spring AI 装配（开放模块）。
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Platform",
        allowedDependencies = {"biztools :: api", "shared", "gatewayclient :: api"}
)
package cn.datafuturex.zhishu.ai.platform;
