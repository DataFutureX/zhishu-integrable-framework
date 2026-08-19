package cn.datafuturex.zhishu.modules.constant;

/**
 * 按钮权限值常量（与 sys_menu.route_name / menu_type=BUTTON 对齐）
 */
public final class PermissionConstants {

    private PermissionConstants() {
    }

    public static final String MENU_TYPE_BUTTON = "BUTTON";
    public static final String MENU_TYPE_MENU = "MENU";
    public static final String ROLE_ADMIN = "ADMIN";

    // 主页
    public static final String HOME_DASHBOARD_QUERY = "home:dashboard:query";

    // 系统管理
    public static final String SYSTEM_USER_QUERY = "system:user:query";
    public static final String SYSTEM_USER_ADD = "system:user:add";
    public static final String SYSTEM_USER_EDIT = "system:user:edit";
    public static final String SYSTEM_USER_REMOVE = "system:user:remove";
    public static final String SYSTEM_USER_ASSIGN_ROLE = "system:user:assignRole";

    public static final String SYSTEM_MENU_QUERY = "system:menu:query";
    public static final String SYSTEM_MENU_ADD = "system:menu:add";
    public static final String SYSTEM_MENU_EDIT = "system:menu:edit";
    public static final String SYSTEM_MENU_REMOVE = "system:menu:remove";

    public static final String SYSTEM_ROLE_QUERY = "system:role:query";
    public static final String SYSTEM_ROLE_ADD = "system:role:add";
    public static final String SYSTEM_ROLE_EDIT = "system:role:edit";
    public static final String SYSTEM_ROLE_REMOVE = "system:role:remove";
    public static final String SYSTEM_ROLE_ASSIGN_MENU = "system:role:assignMenu";

    public static final String SYSTEM_CONFIG_QUERY = "system:config:query";
    public static final String SYSTEM_CONFIG_EDIT = "system:config:edit";

    public static final String SYSTEM_UNIT_QUERY = "system:unit:query";
    public static final String SYSTEM_UNIT_ADD = "system:unit:add";
    public static final String SYSTEM_UNIT_EDIT = "system:unit:edit";
    public static final String SYSTEM_UNIT_REMOVE = "system:unit:remove";

    public static final String SYSTEM_MONITOR_QUERY = "system:monitor:query";
    public static final String SYSTEM_OPERLOG_QUERY = "system:operlog:query";

    public static final String SYSTEM_ANNOUNCEMENT_QUERY = "system:announcement:query";
    public static final String SYSTEM_ANNOUNCEMENT_ADD = "system:announcement:add";
    public static final String SYSTEM_ANNOUNCEMENT_EDIT = "system:announcement:edit";
    public static final String SYSTEM_ANNOUNCEMENT_REMOVE = "system:announcement:remove";
    public static final String SYSTEM_ANNOUNCEMENT_PUBLISH = "system:announcement:publish";

    // 开发工具
    public static final String DEVTOOLS_API_QUERY = "devtools:api:query";

    // 个人中心
    public static final String PROFILE_INFO_QUERY = "profile:info:query";
    public static final String PROFILE_PASSWORD_EDIT = "profile:password:edit";
}
