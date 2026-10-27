/**
 * 开放 API（{@code /open/v1}）：AK/SK 鉴权与对外编排门面。
 * <p>
 * 本模块是「门面」而非业务实现：只允许依赖各业务模块导出的服务契约（{@code :: api}）
 * 与共享层，不得触碰其内部包（实现类、实体、Mapper）。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "OpenAPI",
        allowedDependencies = {
                "shared",
                "chat :: api",
                "agent :: api",
                "knowledge :: api",
                "gatewayclient"
        }
)
package cn.datafuturex.zhishu.ai.openapi;
