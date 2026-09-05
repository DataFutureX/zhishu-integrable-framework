package cn.datafuturex.zhishu.ai.agent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Agent 执行记录列表 VO。
 */
@Schema(description = "Agent 执行记录")
public record AgentExecutionVO(
        @Schema(description = "执行记录 ID") Long id,
        @Schema(description = "智能体 ID") Long agentId,
        @Schema(description = "智能体名称") String agentName,
        @Schema(description = "用户输入原文") String userMessage,
        @Schema(description = "响应摘要") String responseSummary,
        @Schema(description = "执行状态：SUCCESS / FAILED / RUNNING") String status,
        @Schema(description = "执行耗时（毫秒）") Long durationMs,
        @Schema(description = "使用的模型名") String modelName,
        @Schema(description = "工作流类型") String workflowType,
        @Schema(description = "执行类型：CHAT / TRIAL") String runType,
        @Schema(description = "TTFT 首 Token 时间（毫秒）") Long ttftMs,
        @Schema(description = "TPOT 单 Token 平均耗时（毫秒）") Long tpotMs,
        @Schema(description = "响应 Token 总数") Integer tokenCount,
        @Schema(description = "触发人") String userId,
        @Schema(description = "创建时间") LocalDateTime createTime
) {}
