package cn.datafuturex.zhishu.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_run")
public class AiAgentRunEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("agent_id")
    private Long agentId;

    @TableField("conversation_id")
    private String conversationId;

    private String status;

    @TableField("current_node")
    private String currentNode;

    @TableField("state_json")
    private String stateJson;

    // ---- 监控增强字段 ----

    @TableField("user_message")
    private String userMessage;

    @TableField("response_summary")
    private String responseSummary;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("error_message")
    private String errorMessage;

    @TableField("model_name")
    private String modelName;

    @TableField("workflow_type")
    private String workflowType;

    @TableField("user_id")
    private String userId;

    @TableField("run_type")
    private String runType;

    @TableField("ttft_ms")
    private Long ttftMs;

    @TableField("tpot_ms")
    private Long tpotMs;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
