package cn.datafuturex.zhishu.gateway.catalog;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型网关：对外模型目录 —— 表 ai_gateway_model
 */
@Data
@TableName("ai_gateway_model")
public class GatewayModelEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外模型 id（客户端 model 字段取值） */
    private String modelId;

    /** 展示名 */
    private String displayName;

    /** 上游供应商 ID（ai_model_provider.id） */
    private Long providerId;

    /** 供应商真实模型名 */
    private String upstreamModel;

    /** 模型类型：CHAT / EMBEDDING（EMBEDDING 不经网关推理面） */
    private String modelType;

    /** 上下文窗口（tokens），空=未知；可被清空，故需写回 NULL */
    @TableField(value = "context_window", updateStrategy = FieldStrategy.ALWAYS)
    private Integer contextWindow;

    /** 单次输出上限，空=未知 */
    @TableField(value = "max_output_tokens", updateStrategy = FieldStrategy.ALWAYS)
    private Integer maxOutputTokens;

    /** 输入模态，逗号分隔：text,image */
    private String inputModalities;

    /** system 提示词更新策略：in-history / leading-only */
    private String systemPromptUpdate;

    /** reasoning 档位，逗号分隔 */
    @TableField(value = "reasoning_efforts", updateStrategy = FieldStrategy.ALWAYS)
    private String reasoningEfforts;

    /** 默认 reasoning 档位 */
    @TableField(value = "default_reasoning_effort", updateStrategy = FieldStrategy.ALWAYS)
    private String defaultReasoningEffort;

    /** 客户端版本门禁 JSON：{"LetsWork":{"min_version":"0.2.1"}} */
    @TableField(value = "client_requirements", updateStrategy = FieldStrategy.ALWAYS)
    private String clientRequirements;

    /** 透传/覆盖到上游请求体的专有参数 JSON */
    @TableField(value = "extra_params", updateStrategy = FieldStrategy.ALWAYS)
    private String extraParams;

    /** 默认温度（请求未显式传时补齐） */
    @TableField(value = "temperature", updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal temperature;

    /** 默认 Top P */
    @TableField(value = "top_p", updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal topP;

    /** 默认最大输出 token */
    @TableField(value = "max_tokens", updateStrategy = FieldStrategy.ALWAYS)
    private Integer maxTokens;

    /** 是否网关默认模型 */
    private Boolean isDefault;

    /** ENABLED / DISABLED */
    private String status;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    @TableField(value = "remark", updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
