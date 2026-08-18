package cn.datafuturex.yunqi.biz.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 公告创建 DTO
 */
@Schema(description = "公告创建请求")
public record AnnouncementCreateDTO(
        @NotBlank(message = "公告标题不能为空")
        @Schema(description = "公告标题")
        String title,

        @NotBlank(message = "公告内容不能为空")
        @Schema(description = "公告内容")
        String content,

        @Schema(description = "优先级（0-普通，1-重要，2-紧急）", defaultValue = "0")
        Integer priority,

        @Schema(description = "是否立即发布", defaultValue = "false")
        Boolean publishImmediately
) {
    public AnnouncementCreateDTO {
        if (priority == null) {
            priority = 0;
        }
        if (publishImmediately == null) {
            publishImmediately = false;
        }
    }
}
