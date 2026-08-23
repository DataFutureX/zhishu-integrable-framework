package cn.datafuturex.zhishu.ai.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Agent 会话")
public record ChatSessionVO(
        @Schema(description = "会话 ID") String conversationId,
        @Schema(description = "场景 CHAT / DOCUMENT_QA") String scene,
        @Schema(description = "会话标题") String title,
        @Schema(description = "智能体 ID") Long agentId,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "最近更新时间") LocalDateTime updateTime
) {
}
