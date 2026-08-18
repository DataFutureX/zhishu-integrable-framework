package cn.datafuturex.yunqi.biz.operationlog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 操作日志查询 DTO
 */
@Schema(description = "操作日志查询请求")
public record OperationLogQueryDTO(
        @Schema(description = "页码", example = "1", defaultValue = "1")
        Integer pageNum,

        @Schema(description = "每页大小", example = "20", defaultValue = "20")
        Integer pageSize,

        @Schema(description = "操作用户名")
        String username,

        @Schema(description = "模块名称")
        String module,

        @Schema(description = "操作类型")
        String operation,

        @Schema(description = "状态（1-成功，0-失败）")
        Integer status,

        @Schema(description = "操作时间起始")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
        LocalDateTime startTime,

        @Schema(description = "操作时间结束")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
        LocalDateTime endTime
) {
    public OperationLogQueryDTO {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 20;
        }
    }
}
