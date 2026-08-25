package cn.datafuturex.zhishu.biz.announcement.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 公告已读记录实体
 */
@Data
@TableName("sys_announcement_read")
public class AnnouncementReadEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long announcementId;
    private Long userId;
    private LocalDateTime readTime;
}
