package cn.datafuturex.zhishu.biz.announcement.vo;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 公告 VO
 */
@Schema(description = "系统公告")
public record AnnouncementVO(
        @Schema(description = "主键ID")
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @Schema(description = "公告标题")
        String title,
        @Schema(description = "公告内容")
        String content,
        @Schema(description = "优先级（0-普通，1-重要，2-紧急）")
        Integer priority,
        @Schema(description = "状态（0-草稿，1-已发布，2-已撤回）")
        Integer status,
        @Schema(description = "发布时间")
        LocalDateTime publishTime,
        @Schema(description = "发布人ID")
        @JsonSerialize(using = ToStringSerializer.class)
        Long publisherId,
        @Schema(description = "发布人姓名")
        String publisherName,
        @Schema(description = "当前用户是否已读")
        Boolean read,
        @Schema(description = "创建时间")
        LocalDateTime createTime,
        @Schema(description = "更新时间")
        LocalDateTime updateTime
) {
}
