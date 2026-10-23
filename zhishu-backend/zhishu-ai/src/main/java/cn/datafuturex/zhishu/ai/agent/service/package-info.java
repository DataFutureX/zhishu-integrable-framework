/**
 * Agent 模块的服务契约（定义查询、运行时编排等接口）。
 * <p>
 * 与 {@code agent.api}（AgentChatPort）同属一个命名接口 {@code api}；
 * 实现留在 {@code agent.service.impl}，不在导出面内。
 */
@org.springframework.modulith.NamedInterface("api")
package cn.datafuturex.zhishu.ai.agent.service;
