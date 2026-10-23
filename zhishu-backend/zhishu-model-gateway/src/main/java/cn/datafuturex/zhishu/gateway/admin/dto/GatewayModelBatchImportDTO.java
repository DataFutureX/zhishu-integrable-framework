package cn.datafuturex.zhishu.gateway.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * 从上游模型列表批量导入。
 *
 * @param providerId     供应商 ID
 * @param modelType      CHAT / EMBEDDING
 * @param models         上游模型条目（推荐；带 {@code GET /models} 解析出的元数据）
 * @param upstreamModels 仅模型名列表（兼容旧客户端，元数据按默认值处理）
 */
@Schema(description = "批量导入网关模型")
public record GatewayModelBatchImportDTO(
        @NotNull
        @Schema(description = "供应商 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        Long providerId,

        @Pattern(regexp = "^(CHAT|EMBEDDING)$", message = "modelType 只能是 CHAT / EMBEDDING")
        @Schema(description = "模型类型，默认 CHAT")
        String modelType,

        @Schema(description = "上游模型条目列表")
        List<Item> models,

        @Deprecated
        @Schema(description = "（已废弃，兼容旧客户端）上游模型名列表")
        List<String> upstreamModels
) {

    /**
     * 归一化后的待导入条目：优先 {@code models}，其次旧的 {@code upstreamModels}。
     *
     * @return 条目列表（可能为空，由服务层校验）
     */
    public List<Item> resolvedItems() {
        List<Item> resolved = new ArrayList<>();
        if (models != null) {
            for (Item item : models) {
                if (item != null && item.upstreamModel() != null && !item.upstreamModel().isBlank()) {
                    resolved.add(item);
                }
            }
        }
        if (!resolved.isEmpty()) {
            return resolved;
        }
        if (upstreamModels != null) {
            for (String name : upstreamModels) {
                if (name != null && !name.isBlank()) {
                    resolved.add(new Item(name.trim(), null, null, null, null, null, null, null));
                }
            }
        }
        return resolved;
    }

    /**
     * 单个待导入模型。
     *
     * @param upstreamModel          上游模型名（必填）
     * @param displayName              展示名，空则用上游名
     * @param contextWindow          上下文窗口
     * @param maxOutputTokens        单次输出上限
     * @param inputModalities        输入模态，空则按 text
     * @param reasoningEfforts       推理强度档位
     * @param defaultReasoningEffort 默认推理强度
     * @param systemPromptUpdate     system 提示词更新方式，空则按 in-history
     */
    @Schema(description = "待导入的上游模型条目")
    public record Item(
            @Schema(description = "上游模型名", requiredMode = Schema.RequiredMode.REQUIRED)
            String upstreamModel,
            @Schema(description = "展示名")
            String displayName,
            @Schema(description = "上下文窗口")
            Integer contextWindow,
            @Schema(description = "单次输出上限")
            Integer maxOutputTokens,
            @Schema(description = "输入模态")
            List<String> inputModalities,
            @Schema(description = "推理强度档位")
            List<String> reasoningEfforts,
            @Schema(description = "默认推理强度")
            String defaultReasoningEffort,
            @Schema(description = "system 提示词更新方式")
            String systemPromptUpdate
    ) {
    }
}
