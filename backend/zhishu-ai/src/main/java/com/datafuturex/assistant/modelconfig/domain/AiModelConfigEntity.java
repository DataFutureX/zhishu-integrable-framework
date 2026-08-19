package com.datafuturex.assistant.modelconfig.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型运行时配置（单例 id=1）
 */
@Data
@TableName("ai_model_config")
public class AiModelConfigEntity {

    @TableId
    private Long id;

    @TableField("chat_model")
    private String chatModel;

    @TableField("embedding_model")
    private String embeddingModel;

    private BigDecimal temperature;

    @TableField("max_tokens")
    private Integer maxTokens;

    @TableField("top_p")
    private BigDecimal topP;

    @TableField("enable_rag_default")
    private Boolean enableRagDefault;

    @TableField("memory_window_size")
    private Integer memoryWindowSize;

    @TableField("base_url")
    private String baseUrl;

    @TableField("api_key_masked")
    private String apiKeyMasked;

    private String remark;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
