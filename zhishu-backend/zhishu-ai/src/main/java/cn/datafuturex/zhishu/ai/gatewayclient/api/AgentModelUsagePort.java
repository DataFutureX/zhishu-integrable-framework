package cn.datafuturex.zhishu.ai.gatewayclient.api;

import java.util.List;

/**
 * 网关模型与 Agent 的绑定引用端口 —— 由 Agent 模块实现，供模型网关在删除模型前做引用检查。
 * <p>
 * 存在的意义：{@code ai_agent} 是 Agent 模块的表，模型网关不应直连查询；
 * 通过本端口，表结构或绑定语义变化只需调整 Agent 侧实现。
 */
public interface AgentModelUsagePort {

    /**
     * 统计绑定了指定网关模型 id 的 Agent 数量。
     *
     * @param gatewayModelId 网关模型 id（{@code ai_agent.model} 存的值）
     * @return 引用数
     */
    int countAgentsBoundTo(String gatewayModelId);

    /**
     * 取若干绑定 Agent 的名称，用于错误提示。
     *
     * @param gatewayModelId 网关模型 id
     * @return 名称列表（最多 20 个）
     */
    List<String> listAgentNamesBoundTo(String gatewayModelId);
}
