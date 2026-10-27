package cn.datafuturex.zhishu.mcp.web;

import cn.datafuturex.zhishu.common.Result;
import cn.datafuturex.zhishu.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP 控制面异常处理。
 * <p>
 * 必须显式声明：迁出 {@code cn.datafuturex.zhishu.ai} 基包后，
 * {@code AiGlobalExceptionHandler} 不再覆盖本模块，若不接管会落到
 * zhishu-security 的 {@code GlobalExceptionHandler.handleRuntimeException}，
 * 使 {@code BusinessException(404, "上游 MCP 不存在")} 的 code 被压成 400。
 * {@code @Order(HIGHEST_PRECEDENCE)} 与网关的 {@code GatewayExceptionAdvice} 同策略。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "cn.datafuturex.zhishu.mcp.web")
public class McpExceptionAdvice {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("MCP 业务异常: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = joinFieldErrors(e.getBindingResult().getFieldErrors());
        log.warn("MCP 参数校验失败: {}", message);
        return ResponseEntity.badRequest().body(Result.error(400, "参数校验失败: " + message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e) {
        String message = joinFieldErrors(e.getBindingResult().getFieldErrors());
        log.warn("MCP 参数绑定失败: {}", message);
        return ResponseEntity.badRequest().body(Result.error(400, "参数绑定失败: " + message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("MCP 系统异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("系统内部错误，请联系管理员"));
    }

    private static String joinFieldErrors(List<FieldError> errors) {
        return errors.stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
    }
}
