package cn.datafuturex.zhishu.gateway.config;

import cn.datafuturex.zhishu.gateway.compat.GatewayException;
import cn.datafuturex.zhishu.gateway.compat.OpenAiErrors;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 网关协议面异常处理器：{@code /v1} 控制器统一返回 OpenAI 错误体。
 * <p>
 * 只作用于 {@code cn.datafuturex.zhishu.gateway.controller}（对外协议面），
 * 管理面控制器走平台既有 {@code Result} 处理器；通过最高优先级确保不被全局处理器覆盖。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "cn.datafuturex.zhishu.gateway.controller")
public class GatewayExceptionAdvice {

    /**
     * 处理网关业务异常。
     *
     * @param e        异常
     * @param response 响应（已提交时不能再写错误体）
     * @return OpenAI 错误体
     */
    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<String> handleGatewayException(GatewayException e, HttpServletResponse response) {
        log.info("[网关] 返回错误 status={}, code={}, message={}", e.getStatus(), e.getCode(), e.getMessage());
        if (response.isCommitted()) {
            return null;
        }
        return ResponseEntity.status(e.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(OpenAiErrors.body(e));
    }

    /**
     * 数据库结构缺失（如迁移未执行）时给出可操作提示，而不是 500「系统内部错误」。
     *
     * @param e        异常
     * @param response 响应
     * @return OpenAI 错误体
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<String> handleBadSqlGrammar(BadSqlGrammarException e, HttpServletResponse response) {
        log.error("[网关] 数据库结构不匹配，请确认 db/gateway_schema.sql 执行成功", e);
        if (response.isCommitted()) {
            return null;
        }
        GatewayException wrapped = GatewayException.notReady(
                "模型网关数据库结构未完成对齐，请确认 db/gateway_schema.sql 执行成功后重启");
        return ResponseEntity.status(wrapped.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(OpenAiErrors.body(wrapped));
    }

    /**
     * 兜底：协议面任何未分类异常都按 OpenAI 形状返回，避免吐出平台 {@code Result}。
     *
     * @param e        异常
     * @param response 响应
     * @return OpenAI 错误体
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception e, HttpServletResponse response) {
        log.error("[网关] 未处理异常", e);
        if (response.isCommitted()) {
            return null;
        }
        GatewayException wrapped = GatewayException.internal("网关内部错误，请联系管理员");
        return ResponseEntity.status(wrapped.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(OpenAiErrors.body(wrapped));
    }
}
