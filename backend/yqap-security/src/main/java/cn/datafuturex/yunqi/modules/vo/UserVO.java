package cn.datafuturex.yunqi.modules.vo;

import java.time.LocalDateTime;

/**
 * 用户视图对象 VO
 *
 * @author YunQi Application Platform Team
 */
public record UserVO(
        /**
         * 用户ID
         */
        Long id,

        /**
         * 用户名
         */
        String username,

        /**
         * 真实姓名
         */
        String realName,

        /**
         * 邮箱
         */
        String email,

        /**
         * 手机号
         */
        String phone,

        /**
         * 角色编码
         */
        String role,

        /**
         * 角色ID
         */
        Long roleId,

        /**
         * 角色名称
         */
        String roleName,

        /**
         * 用户状态（1-正常，0-禁用）
         */
        Integer status,

        /**
         * 创建时间
         */
        LocalDateTime createTime,

        /**
         * 更新时间
         */
        LocalDateTime updateTime
) {
}
