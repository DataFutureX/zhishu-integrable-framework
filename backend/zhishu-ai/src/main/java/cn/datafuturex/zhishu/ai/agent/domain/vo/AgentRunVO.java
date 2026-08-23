package cn.datafuturex.zhishu.ai.agent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "智能体运行记录")
public record AgentRunVO(
        Long id,
        Long agentId,
        String conversationId,
        String status,
        String currentNode,
        String stateJson,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
