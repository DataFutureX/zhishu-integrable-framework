package cn.datafuturex.zhishu.ai.briefing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简报调度 —— 表 ai_briefing_schedule
 */
@Data
@TableName("ai_briefing_schedule")
public class AiBriefingScheduleEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("agent_id")
    private Long agentId;

    @TableField("prompt_template")
    private String promptTemplate;

    @TableField("scope_type")
    private String scopeType;

    @TableField("schedule_type")
    private String scheduleType;

    @TableField("schedule_time")
    private String scheduleTime;

    @TableField("schedule_days")
    private String scheduleDays;

    @TableField("cron_expr")
    private String cronExpr;

    private String timezone;

    @TableField("next_run_at")
    private LocalDateTime nextRunAt;

    @TableField("last_run_at")
    private LocalDateTime lastRunAt;

    @TableField("notify_bell")
    private Boolean notifyBell;

    @TableField("notify_email")
    private Boolean notifyEmail;

    @TableField("email_to_mode")
    private String emailToMode;

    @TableField("email_extra_to")
    private String emailExtraTo;

    @TableField("email_subject_template")
    private String emailSubjectTemplate;

    private Boolean enabled;

    @TableField("created_by")
    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
