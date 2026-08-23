/**
 * 知枢内部控制台的 Agent 简报：调度、投递、站内铃与邮件。
 * <p>
 * 不对外提供开放 API。外部系统若需要简报，请自行调度并调用 {@code /open/v1/chat}（及 {@code /open/v1/agents}）。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Briefing",
        allowedDependencies = {"shared", "agent", "modelconfig :: api"}
)
package cn.datafuturex.zhishu.ai.briefing;
