package com.datafuturex.assistant.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 聊天请求 DTO（多轮会话 + 可选 RAG）
 */
@Schema(description = "聊天请求对象")
public record ChatRequestDTO(
        @NotBlank(message = "消息不能为空")
        @Schema(description = "用户消息", requiredMode = Schema.RequiredMode.REQUIRED)
        String message,

        @Schema(description = "最大 Token 数量", example = "2000")
        Integer maxTokens,

        @Schema(description = "温度参数", example = "0.7")
        Double temperature,

        @Schema(description = "多轮会话 ID，后续请求回传；为空则新建")
        String conversationId,

        @Schema(description = "是否启用知识库增强（Hybrid 向量+关键词，与文档 QA 共用检索约定）")
        Boolean enableRag,

        @Schema(description = "智能体 ID；为空则使用默认智能体")
        Long agentId
) {
}
