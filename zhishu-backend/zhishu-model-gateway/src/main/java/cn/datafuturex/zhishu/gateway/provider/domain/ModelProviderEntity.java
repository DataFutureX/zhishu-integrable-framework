package cn.datafuturex.zhishu.gateway.provider.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型网关·供应商（仅连接与凭据）—— 表 ai_model_provider
 * <p>
 * 模型名与生成参数一律住在 {@code ai_gateway_model}（模型行），本实体不承载任何模型信息。
 */
@Data
@TableName("ai_model_provider")
public class ModelProviderEntity {

    @TableId
    private Long id;

    private String name;

    @TableField("provider_key")
    private String providerKey;

    @TableField("base_url")
    private String baseUrl;

    @TableField("api_key_enc")
    private String apiKeyEnc;

    @TableField("api_key_masked")
    private String apiKeyMasked;

    @TableField("is_default")
    private Boolean isDefault;

    private String status;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField(value = "remark", updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
