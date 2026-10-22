package cn.datafuturex.zhishu.gateway.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 网关模型新建/修改请求体。
 *
 * @param modelId                 对外模型 id（创建后不可修改）
 * @param displayName             展示名
 * @param providerId              上游供应商 ID
 * @param upstreamModel           供应商真实模型名
 * @param modelType               模型类型：CHAT / EMBEDDING
 * @param contextWindow           上下文窗口
 * @param maxOutputTokens         单次输出上限
 * @param inputModalities         输入模态，逗号分隔
 * @param systemPromptUpdate      system 更新策略
 * @param reasoningEfforts        reasoning 档位，逗号分隔
 * @param defaultReasoningEffort  默认 reasoning 档位
 * @param clientRequirements      客户端版本门禁 JSON
 * @param extraParams             透传上游参数 JSON
 * @param isDefault               是否网关默认模型
 * @param status                  ENABLED / DISABLED
 * @param sortOrder               排序
 * @param remark                  备注
 */
@Schema(description = "网关模型配置")
public record GatewayModelUpsertDTO(

        @NotBlank
        @Pattern(regexp = "^[a-z0-9][a-z0-9.\\-]{0,63}$",
                message = "model_id 只能是小写字母、数字、点与连字符，且以字母或数字开头")
        @Schema(description = "对外模型 id", requiredMode = Schema.RequiredMode.REQUIRED)
        String modelId,

        @NotBlank
        @Size(max = 64)
        @Schema(description = "展示名", requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,

        @NotNull
        @Schema(description = "上游供应商 ID（模型设置）", requiredMode = Schema.RequiredMode.REQUIRED)
        Long providerId,

        @NotBlank
        @Size(max = 128)
        @Schema(description = "供应商真实模型名", requiredMode = Schema.RequiredMode.REQUIRED)
        String upstreamModel,

        @Pattern(regexp = "^(CHAT|EMBEDDING)$", message = "modelType 只能是 CHAT / EMBEDDING")
        @Schema(description = "模型类型，默认 CHAT")
        String modelType,

        @Min(1)
        @Schema(description = "上下文窗口（tokens），空=未知")
        Integer contextWindow,

        @Min(1)
        @Schema(description = "单次输出上限，空=未知")
        Integer maxOutputTokens,

        @Size(max = 64)
        @Schema(description = "输入模态，逗号分隔：text,image")
        String inputModalities,

        @Size(max = 16)
        @Schema(description = "system 更新策略：in-history / leading-only")
        String systemPromptUpdate,

        @Size(max = 64)
        @Schema(description = "reasoning 档位，逗号分隔")
        String reasoningEfforts,

        @Size(max = 16)
        @Schema(description = "默认 reasoning 档位")
        String defaultReasoningEffort,

        @Schema(description = "客户端版本门禁 JSON，如 {\"LetsWork\":{\"min_version\":\"0.2.1\"}}")
        String clientRequirements,

        @Schema(description = "透传上游参数 JSON")
        String extraParams,

        @DecimalMin("0.0")
        @DecimalMax("2.0")
        @Schema(description = "默认温度 0~2")
        BigDecimal temperature,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        @Schema(description = "默认 Top P 0~1")
        BigDecimal topP,

        @Min(1)
        @Schema(description = "默认最大输出 token")
        Integer maxTokens,

        @Schema(description = "是否网关默认模型")
        Boolean isDefault,

        @Size(max = 16)
        @Schema(description = "ENABLED / DISABLED")
        String status,

        @Min(0)
        @Max(9999)
        @Schema(description = "排序")
        Integer sortOrder,

        @Size(max = 500)
        @Schema(description = "备注")
        String remark
) {
}
