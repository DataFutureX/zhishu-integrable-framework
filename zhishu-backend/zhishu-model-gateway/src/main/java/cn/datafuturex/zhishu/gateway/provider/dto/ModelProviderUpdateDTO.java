package cn.datafuturex.zhishu.gateway.provider.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改供应商请求体。
 * <p>
 * v2：只维护连接信息；对话/向量模型与生成参数在「模型设置」中维护。
 */
@Schema(description = "修改供应商")
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
        @Schema(description = "API Key；留空表示不修改已保存密钥")
        String apiKey,

        @Schema(description = "是否默认供应商")
        Boolean isDefault,

        @Schema(description = "是否启用：ENABLED / DISABLED")
        String status,

        @Schema(description = "排序")
        Integer sortOrder,

        @Size(max = 500)
        @Schema(description = "备注")
        String remark
) {
}
