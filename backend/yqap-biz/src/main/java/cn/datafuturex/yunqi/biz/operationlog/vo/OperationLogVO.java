package cn.datafuturex.yunqi.biz.operationlog.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
/**
 * 操作日志 VO
 */
@Schema(description = "操作日志")
public record OperationLogVO(
        @Schema(description = "主键ID")
        Long id,
        @Schema(description = "操作用户ID")
        Long userId,
        @Schema(description = "操作用户名")
        String username,
        @Schema(description = "操作用户真实姓名")
        String realName,
        @Schema(description = "模块名称")
        String module,
        @Schema(description = "操作类型")
        String operation,
        @Schema(description = "请求方法")
        String method,
        @Schema(description = "请求参数")
        String requestParams,
        @Schema(description = "HTTP响应码")
        Integer responseCode,
        @Schema(description = "客户端IP")
        String ipAddress,
        @Schema(description = "User-Agent")
        String userAgent,
        @Schema(description = "耗时（毫秒）")
        Integer durationMs,
        @Schema(description = "状态（1-成功，0-失败）")
        Integer status,
        @Schema(description = "失败原因")
        String errorMessage,
        @Schema(description = "操作时间")
        LocalDateTime createTime
) {
}
