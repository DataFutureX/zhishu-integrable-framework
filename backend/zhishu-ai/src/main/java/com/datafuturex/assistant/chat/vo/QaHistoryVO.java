package com.datafuturex.assistant.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "问答历史记录")
public record QaHistoryVO(
        @Schema(description = "记录 ID") String id,
        @Schema(description = "场景：CHAT / DOCUMENT_QA") String scene,
        @Schema(description = "用户提问") String question,
        @Schema(description = "AI 回答") String answer,
        @Schema(description = "模型") String model,
        @Schema(description = "知识问答指定文档 ID") String documentId,
        @Schema(description = "多轮会话 ID") String conversationId,
        @Schema(description = "创建时间") LocalDateTime createTime) {
}
