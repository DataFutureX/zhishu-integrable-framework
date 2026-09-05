package cn.datafuturex.zhishu.ai.agent.domain.vo;

import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 执行详情 VO（含完整轨迹）。
 */
@Schema(description = "Agent 执行详情")
public record AgentExecutionDetailVO(
        @Schema(description = "执行记录 ID") Long id,
        @Schema(description = "智能体 ID") Long agentId,
        @Schema(description = "智能体名称") String agentName,
        @Schema(description = "用户输入原文") String userMessage,
        @Schema(description = "响应摘要") String responseSummary,
        @Schema(description = "执行状态") String status,
        @Schema(description = "执行耗时（毫秒）") Long durationMs,
        @Schema(description = "使用的模型名") String modelName,
        @Schema(description = "工作流类型") String workflowType,
        @Schema(description = "执行类型：CHAT / TRIAL") String runType,
        @Schema(description = "TTFT（毫秒）") Long ttftMs,
        @Schema(description = "TPOT（毫秒）") Long tpotMs,
        @Schema(description = "响应 Token 总数") Integer tokenCount,
        @Schema(description = "触发人") String userId,
        @Schema(description = "执行轨迹") List<AgentTraceEvent> traces,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime
) {}
