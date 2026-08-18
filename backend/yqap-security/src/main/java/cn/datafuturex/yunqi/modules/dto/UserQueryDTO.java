package cn.datafuturex.yunqi.modules.dto;

/**
 * 用户查询请求 DTO
 *
 * @author YunQi Application Platform Team
 */
public record UserQueryDTO(
        /**
         * 用户名（模糊查询）
         */
        String username,

        /**
         * 真实姓名（模糊查询）
         */
        String realName,

        /**
         * 手机号（模糊查询）
         */
        String phone,

        /**
         * 角色编码（精确查询）
         */
        String role,

        /**
         * 角色ID（精确查询）
         */
        Long roleId,

        /**
         * 用户状态（1-正常，0-禁用）
         */
        Integer status,

        /**
         * 页码
         */
        Integer pageNum,

        /**
         * 每页大小
         */
        Integer pageSize
) {
    public UserQueryDTO {
        // 默认值处理
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }
    }
}
