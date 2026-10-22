package cn.datafuturex.zhishu.gateway.catalog;

import com.baomidou.mybatisplus.annotation.IdType;
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

    /** 上下文窗口（tokens），空=未知 */
    private Integer contextWindow;

    /** 单次输出上限，空=未知 */
    private Integer maxOutputTokens;

    /** 输入模态，逗号分隔：text,image */
    private String inputModalities;

    /** system 提示词更新策略：in-history / leading-only */
    private String systemPromptUpdate;

    /** reasoning 档位，逗号分隔 */
    private String reasoningEfforts;

    /** 默认 reasoning 档位 */
    private String defaultReasoningEffort;

    /** 客户端版本门禁 JSON：{"LetsWork":{"min_version":"0.2.1"}} */
    private String clientRequirements;

    /** 透传/覆盖到上游请求体的专有参数 JSON */
    private String extraParams;

    /** 默认温度（请求未显式传时补齐） */
    private BigDecimal temperature;

    /** 默认 Top P */
    private BigDecimal topP;

    /** 默认最大输出 token */
    private Integer maxTokens;

    /** 是否网关默认模型 */
    private Boolean isDefault;

    /** ENABLED / DISABLED */
    private String status;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
