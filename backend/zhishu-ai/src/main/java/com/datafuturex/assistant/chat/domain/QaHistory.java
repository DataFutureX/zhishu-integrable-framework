package com.datafuturex.assistant.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 问答历史实体 —— 表 qa_history
 */
@TableName("qa_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QaHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    /** CHAT / DOCUMENT_QA */
    @TableField("scene")
    private String scene;

    @TableField("question")
    private String question;

    @TableField("answer")
    private String answer;

    @TableField("model")
    private String model;

    @TableField("document_id")
    private Long documentId;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("agent_id")
    private Long agentId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
