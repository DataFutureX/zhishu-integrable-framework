package cn.datafuturex.zhishu.gateway.port;

/**
 * 模型目标不可用异常 —— 网关无法按配置解析出可用的对话/向量模型时抛出。
 * <p>
 * 属于对外导出面：调用方（AI 内核）据此把「未配置默认模型」「上游缺 Key」等配置问题
 * 统一转成服务不可用响应，而不必让网关反向依赖 AI 的异常体系。
 */
public class ModelTargetException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelTargetException(String message) {
        super(message);
    }

    public ModelTargetException(String message, Throwable cause) {
        super(message, cause);
    }
}
