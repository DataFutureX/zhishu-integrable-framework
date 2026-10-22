package cn.datafuturex.zhishu.gateway.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 从上游模型列表批量导入。
 *
 * @param providerId     供应商 ID
 * @param modelType      CHAT / EMBEDDING
 * @param upstreamModels 上游模型名列表
 */
@Schema(description = "批量导入网关模型")
public record GatewayModelBatchImportDTO(

        @NotNull
        @Schema(description = "供应商 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        Long providerId,

        @Pattern(regexp = "^(CHAT|EMBEDDING)$", message = "modelType 只能是 CHAT / EMBEDDING")
        @Schema(description = "模型类型，默认 CHAT")
        String modelType,

        @NotEmpty
        @Schema(description = "上游模型名列表", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> upstreamModels
) {
}
