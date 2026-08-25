package cn.datafuturex.zhishu.ai.openapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 创建/更新开放应用请求。
 */
public record OpenAppUpsertDTO(

        @NotBlank(message = "编码不能为空")
        @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$",
                message = "编码须以小写字母开头，仅含小写字母、数字、下划线或连字符，最长 64 字符")
        String code,

        @NotBlank(message = "名称不能为空")
        String name,

        String remark,

        java.util.List<String> allowedScopes
) {
}
