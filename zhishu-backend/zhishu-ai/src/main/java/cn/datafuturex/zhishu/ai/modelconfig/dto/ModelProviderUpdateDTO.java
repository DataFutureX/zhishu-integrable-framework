package cn.datafuturex.zhishu.ai.modelconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 更新模型设置请求体
 */
@Schema(description = "更新模型设置")
public record ModelProviderUpdateDTO(

        @NotBlank
        @Size(max = 64)
        @Schema(description = "展示名称", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @NotBlank
        @Size(max = 512)
        @Schema(description = "OpenAI 兼容 Base URL", requiredMode = Schema.RequiredMode.REQUIRED)
        String baseUrl,

        @Size(max = 512)
        @Schema(description = "模型 API Key；留空表示不修改已保存密钥")
        String apiKey,

        @NotBlank
        @Size(max = 64)
        @Schema(description = "对话模型名", requiredMode = Schema.RequiredMode.REQUIRED)
        String chatModel,

        @Size(max = 64)
        @Schema(description = "向量模型名（仅默认模型设置需要）")
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

        @Schema(description = "是否启用")
        String status,

        @Schema(description = "排序")
        Integer sortOrder,

        @Size(max = 500)
        @Schema(description = "备注")
        String remark
) {
}
