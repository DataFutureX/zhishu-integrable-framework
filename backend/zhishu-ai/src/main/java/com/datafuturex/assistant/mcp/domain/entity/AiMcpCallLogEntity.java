package com.datafuturex.assistant.mcp.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_mcp_call_log")
public class AiMcpCallLogEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String direction;

    @TableField("client_id")
    private Long clientId;

    @TableField("upstream_id")
    private Long upstreamId;

    @TableField("agent_id")
    private Long agentId;

    @TableField("tool_name")
    private String toolName;

    private Boolean success;

    @TableField("error_message")
    private String errorMessage;

    @TableField("duration_ms")
    private Integer durationMs;

    @TableField("user_id")
    private String userId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
