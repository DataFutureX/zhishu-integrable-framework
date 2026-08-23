package cn.datafuturex.zhishu.ai.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cn.datafuturex.zhishu.ai.shared.Result;
import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;

import java.util.stream.Collectors;

/**
 * AI 模块全局异常处理器（与 zhishu-security 的 GlobalExceptionHandler 并存）。
 */
@RestControllerAdvice(basePackages = "cn.datafuturex.zhishu.ai")
@Slf4j
public class AiGlobalExceptionHandler {

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
                log.warn("业务异常: {}", e.getMessage());
                return ResponseEntity.badRequest()
                                .body(Result.fail(e.getCode(), e.getMessage()));
        }

        @ExceptionHandler(AiException.class)
        public ResponseEntity<Result<Void>> handleAiException(AiException e) {
                log.error("AI 服务异常: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(Result.fail(e.getCode(), "AI 服务暂时不可用，请稍后重试"));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException e) {
                String message = e.getBindingResult().getFieldErrors().stream()
                                .map(FieldError::getDefaultMessage)
                                .collect(Collectors.joining("; "));
                log.warn("参数校验失败: {}", message);
                return ResponseEntity.badRequest()
                                .body(Result.fail(400, "参数校验失败: " + message));
        }

        @ExceptionHandler(BindException.class)
        public ResponseEntity<Result<Void>> handleBindException(BindException e) {
                String message = e.getBindingResult().getFieldErrors().stream()
                                .map(FieldError::getDefaultMessage)
                                .collect(Collectors.joining("; "));
                log.warn("参数绑定失败: {}", message);
                return ResponseEntity.badRequest()
                                .body(Result.fail(400, "参数绑定失败: " + message));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Result<Void>> handleException(Exception e) {
                log.error("系统异常: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Result.fail("系统内部错误，请联系管理员"));
        }
}
