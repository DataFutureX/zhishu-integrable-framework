package com.datafuturex.assistant.briefing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简报投递 —— 表 ai_briefing_delivery
 */
@Data
@TableName("ai_briefing_delivery")
public class AiBriefingDeliveryEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("schedule_id")
    private Long scheduleId;

    @TableField("trigger_type")
    private String triggerType;

    @TableField("trigger_ref")
    private String triggerRef;

    @TableField("user_id")
    private String userId;

    @TableField("agent_id")
    private Long agentId;

    @TableField("run_id")
    private Long runId;

    private String title;

    @TableField("content_md")
    private String contentMd;

    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("read_at")
    private LocalDateTime readAt;

    @TableField("bell_notified_at")
    private LocalDateTime bellNotifiedAt;

    @TableField("email_status")
    private String emailStatus;

    @TableField("email_to")
    private String emailTo;

    @TableField("email_error")
    private String emailError;

    @TableField("email_sent_at")
    private LocalDateTime emailSentAt;

    @TableField("webhook_status")
    private String webhookStatus;

    @TableField("webhook_error")
    private String webhookError;

    @TableField("webhook_sent_at")
    private LocalDateTime webhookSentAt;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
