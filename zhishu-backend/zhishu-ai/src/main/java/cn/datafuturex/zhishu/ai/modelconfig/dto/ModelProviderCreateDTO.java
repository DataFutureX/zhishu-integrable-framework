package cn.datafuturex.zhishu.ai.modelconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建供应商请求体。
 * <p>
 * v2：供应商只承载连接与凭据；对话/向量模型与生成参数在「模型设置」中维护。
 */
@Schema(description = "新建供应商")
public record ModelProviderCreateDTO(

        @NotBlank
        @Size(max = 64)
        @Schema(description = "展示名称（如阿里百炼、DeepSeek）", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @NotBlank
        @Size(max = 64)
        @Schema(description = "程序标识（如 dashscope / deepseek / openai）", requiredMode = Schema.RequiredMode.REQUIRED)
        String providerKey,

        @NotBlank
        @Size(max = 512)
        @Schema(description = "OpenAI 兼容 Base URL", requiredMode = Schema.RequiredMode.REQUIRED)
        String baseUrl,

        @Size(max = 512)
        @Schema(description = "API Key（加密入库）")
        String apiKey,

        @Schema(description = "是否默认供应商")
        Boolean isDefault,

        @Schema(description = "排序")
        Integer sortOrder,

        @Size(max = 500)
        @Schema(description = "备注")
        String remark
) {
}
