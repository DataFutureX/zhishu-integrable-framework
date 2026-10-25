package cn.datafuturex.zhishu.gateway.provider.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 上游模型元数据（由供应商 {@code GET /models} 或 Ollama 的 {@code GET /api/tags} 解析而来）。
 * <p>
 * 各厂商字段覆盖度不一，缺省的字段一律为 {@code null}，由调用方决定回退策略。
 *
 * @param id                    上游模型名（请求体 model 字段实际取值）
 * @param name                  上游展示名，缺省时等于 id
 * @param contextWindow         上下文窗口
 * @param maxOutputTokens       单次输出上限
 * @param inputModalities       输入模态，如 text / image
 * @param reasoningEfforts      支持的推理强度档位
 * @param defaultReasoningEffort 默认推理强度档位
 * @param systemPromptUpdate    system 提示词更新方式：in-history / leading-only
 */
@Schema(description = "上游模型元数据")
public record UpstreamModelInfo(
        @Schema(description = "上游模型名") String id,
        @Schema(description = "上游展示名") String name,
        @Schema(description = "上下文窗口") Integer contextWindow,
        @Schema(description = "单次输出上限") Integer maxOutputTokens,
        @Schema(description = "输入模态") List<String> inputModalities,
        @Schema(description = "支持的推理强度档位") List<String> reasoningEfforts,
        @Schema(description = "默认推理强度档位") String defaultReasoningEffort,
        @Schema(description = "system 提示词更新方式") String systemPromptUpdate
) {
}
