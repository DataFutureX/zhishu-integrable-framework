package cn.datafuturex.zhishu.modules.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 启用/禁用用户请求
 */
public record UserStatusUpdateDTO(
        /**
         * 用户状态（1-正常，0-禁用）
         */
        @NotNull(message = "状态不能为空")
        Integer status
) {
}
