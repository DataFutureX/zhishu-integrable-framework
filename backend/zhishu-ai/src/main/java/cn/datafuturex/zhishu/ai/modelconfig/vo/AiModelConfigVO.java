package cn.datafuturex.zhishu.ai.modelconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "AI 模型配置")
public record AiModelConfigVO(
        @Schema(description = "对话模型") String chatModel,
        @Schema(description = "向量模型") String embeddingModel,
        @Schema(description = "温度") BigDecimal temperature,
        @Schema(description = "最大 Token") Integer maxTokens,
        @Schema(description = "Top P") BigDecimal topP,
        @Schema(description = "对话默认开启知识库 RAG") Boolean enableRagDefault,
        @Schema(description = "会话记忆窗口消息数") Integer memoryWindowSize,
        @Schema(description = "API Base URL") String baseUrl,
        @Schema(description = "API Key 脱敏展示") String apiKeyMasked,
        @Schema(description = "是否已配置 API Key") Boolean apiKeyConfigured,
        @Schema(description = "备注") String remark,
        @Schema(description = "可选对话模型列表") List<String> chatModelOptions,
        @Schema(description = "可选向量模型列表") List<String> embeddingModelOptions,
        @Schema(description = "更新时间") LocalDateTime updateTime
) {
}
