package cn.datafuturex.zhishu.ai.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 文档问答请求 DTO
 */
@Schema(description = "文档问答请求对象")
public record DocumentQueryDTO(
        @NotBlank(message = "问题不能为空")
        @Schema(description = "用户提出的问题", example = "文档中关于水位监测的内容是什么？",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String question,

        @Schema(description = "指定查询的文档ID，不填则按知识库/全库检索", example = "1")
        Long documentId,

        @Schema(description = "知识库分类 ID；指定后仅在该知识库内检索（可与 documentId 联用校验归属）", example = "1")
        Long categoryId,

        @Schema(description = "返回最相关的片段数量", example = "5", minimum = "1", maximum = "10")
        Integer topK,

        @Schema(description = "多轮会话 ID，后续请求回传")
        String conversationId
) {
}
