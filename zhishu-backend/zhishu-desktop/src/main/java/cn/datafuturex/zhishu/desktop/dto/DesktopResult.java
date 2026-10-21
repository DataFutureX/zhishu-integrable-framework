package cn.datafuturex.zhishu.desktop.dto;

/**
 * 桌面端通用响应封装。
 *
 * @param code    状态码（200 成功，其他为错误）
 * @param message 描述信息
 * @param data    响应数据
 */
public record DesktopResult<T>(
        int code,
        String message,
        T data
) {

    public static <T> DesktopResult<T> ok(T data) {
        return new DesktopResult<>(200, "success", data);
    }

    public static <T> DesktopResult<T> ok(T data, String message) {
        return new DesktopResult<>(200, message, data);
    }

    public static <T> DesktopResult<T> error(int code, String message) {
        return new DesktopResult<>(code, message, null);
    }
}
