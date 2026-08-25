/**
 * 智能体定义、Graph、运行时。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Agent",
        allowedDependencies = {"shared", "knowledge :: api", "biztools :: api", "modelconfig :: api"}
)
package cn.datafuturex.zhishu.ai.agent;
