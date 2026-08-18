package cn.datafuturex.yunqi.modules.vo;

/**
 * 用户角色视图对象
 */
public record UserRoleVO(
        Long userId,
        Long roleId,
        String roleCode,
        String roleName
) {
}
