package com.datafuturex.assistant.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智能体定义 —— 表 ai_agent
 */
@Data
@TableName("ai_agent")
public class AiAgentEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String description;

    @TableField("system_prompt")
    private String systemPrompt;

    private String model;

    private BigDecimal temperature;

    @TableField("max_tokens")
    private Integer maxTokens;

    /** JSON 数组字符串 */
    private String capabilities;

    @TableField("workflow_type")
    private String workflowType;

    @TableField("workflow_config")
    private String workflowConfig;

    /** JSON 数组字符串，绑定知识库文档 ID；空表示全部 */
    @TableField(value = "document_ids", updateStrategy = FieldStrategy.ALWAYS)
    private String documentIds;

    @TableField("enable_memory")
    private Boolean enableMemory;

    private String status;

    @TableField("is_builtin")
    private Boolean builtin;

    @TableField("is_default")
    private Boolean defaultAgent;

    @TableField("created_by")
    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
