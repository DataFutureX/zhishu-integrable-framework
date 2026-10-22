package cn.datafuturex.zhishu.gateway.usage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型网关：调用台账 —— 表 ai_gateway_usage
 */
@Data
@TableName("ai_gateway_usage")
public class GatewayUsageEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单次请求唯一标识 */
    private String requestId;

    /** 用户标识（与 qa_history.user_id 一致，存 username） */
    private String userId;

    private String username;

    /** DESKTOP / CONSOLE / AGENT / BRIEFING / OPENAPI / TEST */
    private String channel;

    private String modelId;

    private Long providerId;

    private String upstreamModel;

    private Boolean stream;

    /** RESERVED / SUCCESS / ERROR / REJECTED_QUOTA / ABORTED / TIMEOUT */
    private String status;

    private Integer httpStatus;

    private Long reservedTokens;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer cachedTokens;

    private Integer reasoningTokens;

    /** usage 是否为网关估算 */
    private Boolean estimated;

    private Integer latencyMs;

    private Integer ttftMs;

    private String clientName;

    private String clientVersion;

    private String ip;

    private String userAgent;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
