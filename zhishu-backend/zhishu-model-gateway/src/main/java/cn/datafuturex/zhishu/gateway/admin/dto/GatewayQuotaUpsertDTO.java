package cn.datafuturex.zhishu.gateway.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 额度策略新建/修改请求体。
 *
 * @param subjectType 主体类型：USER / ROLE / DEFAULT
 * @param subjectKey  主体键（USER=username，ROLE=role_code，DEFAULT=*）
 * @param periodKind  周期类型（MONTHLY）
 * @param tokenLimit  周期内 token 上限，null=不限量
 * @param status      ENABLED / DISABLED
 * @param remark      备注
 */
@Schema(description = "额度策略")
public record GatewayQuotaUpsertDTO(

        @NotBlank
        @Pattern(regexp = "^(USER|ROLE|DEFAULT)$", message = "subjectType 只能是 USER / ROLE / DEFAULT")
        @Schema(description = "主体类型", requiredMode = Schema.RequiredMode.REQUIRED)
        String subjectType,

        @NotBlank
        @Size(max = 64)
        @Schema(description = "主体键：USER=username，ROLE=role_code，DEFAULT=*",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String subjectKey,

        @Size(max = 16)
        @Schema(description = "周期类型，默认 MONTHLY")
        String periodKind,

        @Min(0)
        @Schema(description = "周期内 token 上限，留空=不限量")
        Long tokenLimit,

        @Size(max = 16)
        @Schema(description = "ENABLED / DISABLED")
        String status,

        @Size(max = 500)
        @Schema(description = "备注")
        String remark
) {

    /**
     * 规范化主体键：DEFAULT 主体固定使用 {@code *}。
     *
     * @return 规范化后的主体键
     */
    public String normalizedSubjectKey() {
        return "DEFAULT".equalsIgnoreCase(subjectType) ? "*" : subjectKey.trim();
    }

    /**
     * 规范化主体类型。
     *
     * @return 大写主体类型
     */
    public String normalizedSubjectType() {
        return subjectType == null ? null : subjectType.trim().toUpperCase();
    }
}
