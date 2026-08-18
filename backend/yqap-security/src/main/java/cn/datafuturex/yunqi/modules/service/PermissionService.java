package cn.datafuturex.yunqi.modules.service;

import java.util.List;

/**
 * 当前用户按钮权限查询
 */
public interface PermissionService {

    /**
     * 按用户名查询已授权的按钮权限值（sys_menu.route_name，menu_type=BUTTON）
     * ADMIN 返回全部启用的按钮权限。
     * 用户不存在或已禁用时返回 null（调用方据此拒绝认证）。
     */
    List<String> listPermissionCodesByUsername(String username);

    /**
     * 清除指定用户权限缓存
     */
    void evictCache(String username);

    /**
     * 清除全部权限缓存
     */
    void evictAllCache();
}
