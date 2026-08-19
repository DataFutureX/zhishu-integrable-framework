package com.datafuturex.assistant.modelconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "更新 AI 模型配置")
public record AiModelConfigUpdateDTO(
        @NotBlank
        @Size(max = 64)
        @Schema(description = "对话模型", requiredMode = Schema.RequiredMode.REQUIRED)
        String chatModel,

        @NotBlank
        @Size(max = 64)
        @Schema(description = "向量模型", requiredMode = Schema.RequiredMode.REQUIRED)
        String embeddingModel,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("2.0")
        @Schema(description = "温度 0~2", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal temperature,

        @NotNull
        @Min(256)
        @Max(8192)
        @Schema(description = "最大 Token", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer maxTokens,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        @Schema(description = "Top P 0~1", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal topP,

        @NotNull
        @Schema(description = "对话默认开启知识库 RAG", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean enableRagDefault,

        @NotNull
        @Min(4)
        @Max(100)
        @Schema(description = "会话记忆窗口消息数", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer memoryWindowSize,

        @Size(max = 500)
        @Schema(description = "备注")
        String remark
) {
}
