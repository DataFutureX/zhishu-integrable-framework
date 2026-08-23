package cn.datafuturex.zhishu.ai.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "修改会话标题")
public record ChatSessionTitleDTO(
        @NotBlank @Schema(description = "新标题") String title
) {
}
