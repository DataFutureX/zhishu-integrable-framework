package cn.datafuturex.zhishu.gateway.provider.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 供应商响应体（v2：只含连接与凭据信息）。
 */
@Schema(description = "供应商")
public record ModelProviderVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "展示名称") String name,
        @Schema(description = "程序标识") String providerKey,
        @Schema(description = "Base URL") String baseUrl,
        @Schema(description = "API Key 脱敏展示") String apiKeyMasked,
        @Schema(description = "是否已配置 API Key") Boolean apiKeyConfigured,
        @Schema(description = "是否为默认供应商") Boolean isDefault,
        @Schema(description = "状态：ENABLED / DISABLED") String status,
        @Schema(description = "排序") Integer sortOrder,
        @Schema(description = "备注") String remark,
        @Schema(description = "更新时间") LocalDateTime updateTime
) {
}
