package com.datafuturex.assistant.agent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "系统提示词初稿")
public record AgentPromptDraftVO(
        @Schema(description = "提示词正文")
        String prompt,

        @Schema(description = "LLM=模型生成；TEMPLATE=按表单拼装")
        String source
) {
}
