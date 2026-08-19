package cn.datafuturex.zhishu.biz.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 公告更新 DTO
 */
@Schema(description = "公告更新请求")
public record AnnouncementUpdateDTO(
        @NotNull(message = "公告ID不能为空")
        @Schema(description = "公告ID")
        Long id,

        @Schema(description = "公告标题")
        String title,

        @Schema(description = "公告内容")
        String content,

        @Schema(description = "优先级（0-普通，1-重要，2-紧急）")
        Integer priority
) {
}
