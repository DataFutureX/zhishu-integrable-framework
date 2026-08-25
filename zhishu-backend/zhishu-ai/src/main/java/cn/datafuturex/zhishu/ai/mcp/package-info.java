/**
 * MCP 双平面：对外 Server + 接入 Client。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Mcp",
        allowedDependencies = {"shared", "biztools :: api"}
)
package cn.datafuturex.zhishu.ai.mcp;
