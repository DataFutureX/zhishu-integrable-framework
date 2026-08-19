package com.datafuturex.assistant.openapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "开放 API：为当前代调用户生成简报")
public record OpenBriefingGenerateDTO(
        @Schema(description = "调度 ID；为空则取第一条启用调度")
        Long scheduleId,
        @Schema(description = "覆盖提示词；为空则用调度模板")
        String prompt
) {
}
