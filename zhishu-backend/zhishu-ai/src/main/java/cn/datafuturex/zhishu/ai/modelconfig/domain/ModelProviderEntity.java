package cn.datafuturex.zhishu.ai.modelconfig.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型设置（多供应商）—— 表 ai_model_provider
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

    @TableField("chat_model")
    private String chatModel;

    @TableField("embedding_model")
    private String embeddingModel;

    private BigDecimal temperature;

    @TableField("max_tokens")
    private Integer maxTokens;

    @TableField("top_p")
    private BigDecimal topP;

    @TableField("is_default")
    private Boolean isDefault;

    private String status;

    @TableField("sort_order")
    private Integer sortOrder;

    private String remark;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
