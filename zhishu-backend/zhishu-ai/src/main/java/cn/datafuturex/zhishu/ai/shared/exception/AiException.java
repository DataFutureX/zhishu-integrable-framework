package cn.datafuturex.zhishu.ai.shared.exception;

import lombok.Getter;

/**
 * AI 服务异常类
 * 
 * @author Qoder
 * @since 1.0.0
 */
@Getter
public class AiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final Integer code;

    public AiException(String message) {
        super(message);
        this.code = 503;
    }

    public AiException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
        this.code = 503;
    }

    public AiException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
