package cn.datafuturex.zhishu.biz.operationlog.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 系统操作日志实体
 */
@Data
@TableName("sys_operation_log")
public class OperationLogEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String username;
    private String realName;
    private String module;
    private String operation;
    private String method;
    private String requestParams;
    private Integer responseCode;
    private String ipAddress;
    private String userAgent;
    private Integer durationMs;
    /** 状态（1-成功，0-失败） */
    private Integer status;
    private String errorMessage;
    private LocalDateTime createTime;
}
