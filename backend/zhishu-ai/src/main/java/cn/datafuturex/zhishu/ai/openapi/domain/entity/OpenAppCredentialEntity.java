package cn.datafuturex.zhishu.ai.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("open_app_credential")
public class OpenAppCredentialEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("app_id")
    private Long appId;

    @TableField("key_prefix")
    private String keyPrefix;

    @TableField("secret_hash")
    private String secretHash;

    private String status;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
