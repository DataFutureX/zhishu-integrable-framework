package com.datafuturex.assistant.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "裁剪会话后续轮次（编辑提问前）")
public record ChatSessionTruncateDTO(
        @NotNull
        @Min(0)
        @Schema(description = "保留前 N 轮用户提问，其后的问答与 Memory 一并删除")
        Integer keepUserTurns
) {
}
