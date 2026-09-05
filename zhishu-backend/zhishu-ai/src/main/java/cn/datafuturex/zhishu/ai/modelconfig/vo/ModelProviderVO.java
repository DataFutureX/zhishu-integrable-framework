package cn.datafuturex.zhishu.ai.modelconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型设置响应体
 */
@Schema(description = "模型设置")
public record ModelProviderVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "展示名称") String name,
        @Schema(description = "程序标识") String providerKey,
        @Schema(description = "Base URL") String baseUrl,
        @Schema(description = "API Key 脱敏展示") String apiKeyMasked,
        @Schema(description = "是否已配置 API Key") Boolean apiKeyConfigured,
        @Schema(description = "对话模型名") String chatModel,
        @Schema(description = "向量模型名") String embeddingModel,
        @Schema(description = "温度") BigDecimal temperature,
        @Schema(description = "最大 Token") Integer maxTokens,
        @Schema(description = "Top P") BigDecimal topP,
        @Schema(description = "是否为默认模型设置") Boolean isDefault,
        @Schema(description = "状态") String status,
        @Schema(description = "排序") Integer sortOrder,
        @Schema(description = "备注") String remark,
        @Schema(description = "更新时间") LocalDateTime updateTime
) {
}
