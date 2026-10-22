package cn.datafuturex.zhishu.gateway.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 供应商管理视图：连接信息 + 模型数量。
 *
 * @param id              主键
 * @param name            展示名
 * @param providerKey     程序标识
 * @param baseUrl         Base URL
 * @param apiKeyMasked    Key 脱敏
 * @param apiKeyConfigured 是否已配置 Key
 * @param isDefault       是否默认供应商
 * @param status          状态
 * @param sortOrder       排序
 * @param remark          备注
 * @param chatModelCount  对话模型数量
 * @param embeddingModelCount 向量模型数量
 * @param updateTime      更新时间
 */
@Schema(description = "供应商")
public record GatewayProviderAdminVO(
        Long id,
        String name,
        String providerKey,
        String baseUrl,
        String apiKeyMasked,
        Boolean apiKeyConfigured,
        Boolean isDefault,
        String status,
        Integer sortOrder,
        String remark,
        long chatModelCount,
        long embeddingModelCount,
        LocalDateTime updateTime
) {
}
