package com.datafuturex.assistant.mcp.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_mcp_upstream")
public class AiAgentMcpUpstreamEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("agent_id")
    private Long agentId;

    @TableField("upstream_id")
    private Long upstreamId;

    @TableField("allowed_tools")
    private String allowedTools;

    @TableField("create_time")
    private LocalDateTime createTime;
}
