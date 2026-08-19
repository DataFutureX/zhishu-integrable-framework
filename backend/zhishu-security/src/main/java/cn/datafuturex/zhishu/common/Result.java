package cn.datafuturex.zhishu.common;

import lombok.Data;

/**
 * 统一响应结果类
 *
 * @param <T> 数据类型
 * @author YunQi Application Platform Team
 */
@Data
public class Result<T> {

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    /**
     * 成功响应
     *
     * @param data 数据
     * @return Result
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    /**
     * 成功响应（无数据）
     *
     * @return Result
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 失败响应
     *
     * @param message 错误消息
     * @return Result
     */
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }

    /**
     * 失败响应（自定义状态码）
     *
     * @param code    状态码
     * @param message 错误消息
     * @return Result
     */
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
