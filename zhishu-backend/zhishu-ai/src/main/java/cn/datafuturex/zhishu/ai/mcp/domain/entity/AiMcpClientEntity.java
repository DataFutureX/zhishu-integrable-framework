package cn.datafuturex.zhishu.ai.mcp.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_mcp_client")
public class AiMcpClientEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("key_prefix")
    private String keyPrefix;

    @TableField("secret_hash")
    private String secretHash;

    @TableField("bound_user_id")
    private Long boundUserId;

    @TableField("bound_username")
    private String boundUsername;

    private String capabilities;

    @TableField("rpm_limit")
    private Integer rpmLimit;

    private String status;

    private String remark;

    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
