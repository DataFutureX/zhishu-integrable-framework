package cn.datafuturex.zhishu.biz.announcement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 公告查询 DTO
 */
@Schema(description = "公告查询请求")
public record AnnouncementQueryDTO(
        @Schema(description = "页码", example = "1", defaultValue = "1")
        Integer pageNum,

        @Schema(description = "每页大小", example = "20", defaultValue = "20")
        Integer pageSize,

        @Schema(description = "标题关键词")
        String title,

        @Schema(description = "优先级（0-普通，1-重要，2-紧急）")
        Integer priority,

        @Schema(description = "状态（0-草稿，1-已发布，2-已撤回）")
        Integer status,

        @Schema(description = "仅查询未读（铃铛下拉场景）")
        Boolean unreadOnly,

        @Schema(description = "发布时间起始")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
        LocalDateTime startTime,

        @Schema(description = "发布时间结束")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
        LocalDateTime endTime
) {
    public AnnouncementQueryDTO {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 20;
        }
    }
}
