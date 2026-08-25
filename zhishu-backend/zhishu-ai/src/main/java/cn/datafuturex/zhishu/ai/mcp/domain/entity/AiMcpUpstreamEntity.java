package cn.datafuturex.zhishu.ai.mcp.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_mcp_upstream")
public class AiMcpUpstreamEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String protocol;

    @TableField("base_url")
    private String baseUrl;

    private String endpoint;

    @TableField("auth_header_enc")
    private String authHeaderEnc;

    @TableField("request_timeout_ms")
    private Integer requestTimeoutMs;

    private String status;

    @TableField("health_status")
    private String healthStatus;

    @TableField("health_message")
    private String healthMessage;

    @TableField("last_probe_at")
    private LocalDateTime lastProbeAt;

    private String remark;

    @TableField("created_by")
    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
