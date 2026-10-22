package cn.datafuturex.zhishu.gateway.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 网关模型管理视图。
 *
 * @param id                      主键
 * @param modelId                 对外模型 id
 * @param displayName             展示名
 * @param providerId              上游供应商 ID
 * @param providerName            上游供应商名称
 * @param providerKey             上游供应商标识
 * @param upstreamModel           供应商真实模型名
 * @param modelType               模型类型：CHAT / EMBEDDING
 * @param contextWindow           上下文窗口
 * @param maxOutputTokens         单次输出上限
 * @param inputModalities         输入模态
 * @param systemPromptUpdate      system 更新策略
 * @param reasoningEfforts        reasoning 档位
 * @param defaultReasoningEffort  默认档位
 * @param clientRequirements      客户端门禁 JSON
 * @param extraParams             透传参数 JSON
 * @param isDefault               是否默认模型
 * @param status                  ENABLED / DISABLED
 * @param sortOrder               排序
 * @param remark                  备注
 * @param upstreamUsable          上游是否可用（Key/BaseURL 齐备且启用）
 * @param createTime              创建时间
 * @param updateTime              更新时间
 */
@Schema(description = "网关模型")
public record GatewayModelAdminVO(
        Long id,
        String modelId,
        String displayName,
        Long providerId,
        String providerName,
        String providerKey,
        String upstreamModel,
        String modelType,
        Integer contextWindow,
        Integer maxOutputTokens,
        String inputModalities,
        String systemPromptUpdate,
        String reasoningEfforts,
        String defaultReasoningEffort,
        String clientRequirements,
        String extraParams,
        BigDecimal temperature,
        BigDecimal topP,
        Integer maxTokens,
        Boolean isDefault,
        String status,
        Integer sortOrder,
        String remark,
        Boolean upstreamUsable,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
