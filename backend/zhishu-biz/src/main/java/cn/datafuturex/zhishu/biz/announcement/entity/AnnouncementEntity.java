package cn.datafuturex.zhishu.biz.announcement.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 系统公告实体
 */
@Data
@TableName("sys_announcement")
public class AnnouncementEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String title;
    private String content;
    /** 优先级（0-普通，1-重要，2-紧急） */
    private Integer priority;
    /** 状态（0-草稿，1-已发布，2-已撤回） */
    private Integer status;
    private LocalDateTime publishTime;
    private Long publisherId;
    private String publisherName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
