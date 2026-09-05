package cn.datafuturex.zhishu.ai.agent.service;

import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentRunEntity;
import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;

import java.util.List;

public interface AgentRunService {

    /**
     * 开始一次 Agent 执行记录。
     *
     * @param agentId  智能体 ID
     * @param conversationId 会话 ID
     * @return 新建的执行记录实体
     */
    AiAgentRunEntity start(Long agentId, String conversationId);

    /**
     * 开始一次 Agent 执行记录（含监控字段）。
     *
     * @param agentId        智能体 ID
     * @param conversationId 会话 ID
     * @param userMessage    用户输入原文
     * @param modelName      使用的模型名
     * @param workflowType   工作流类型
     * @param userId         触发人
     * @param runType        CHAT | TRIAL
     * @return 新建的执行记录实体
     */
    AiAgentRunEntity start(Long agentId, String conversationId,
                           String userMessage, String modelName,
                           String workflowType, String userId, String runType);

    /**
     * 完成一次 Agent 执行记录（基础）。
     *
     * @param runId       执行记录 ID
     * @param status      SUCCESS / FAILED
     * @param currentNode 最后节点名
     * @param traces      轨迹事件列表
     */
    void complete(Long runId, String status, String currentNode, List<AgentTraceEvent> traces);

    /**
     * 完成一次 Agent 执行记录（含监控字段）。
     *
     * @param runId          执行记录 ID
     * @param status         SUCCESS / FAILED
     * @param currentNode    最后节点名
     * @param traces         轨迹事件列表
     * @param durationMs     执行耗时（毫秒）
     * @param responseSummary 响应摘要
     * @param errorMessage   失败原因（成功时传 null）
     * @param ttftMs         TTFT（毫秒），-1 表示无数据
     * @param tpotMs         TPOT（毫秒），-1 表示无数据
     * @param tokenCount     响应 Token 总数
     */
    void complete(Long runId, String status, String currentNode, List<AgentTraceEvent> traces,
                  Long durationMs, String responseSummary, String errorMessage,
                  Long ttftMs, Long tpotMs, Integer tokenCount);

    /**
     * 查询最近执行记录。
     *
     * @param agentId 智能体 ID
     * @param limit   最多返回条数
     * @return 执行记录列表
     */
    List<AiAgentRunEntity> recent(Long agentId, int limit);
}
