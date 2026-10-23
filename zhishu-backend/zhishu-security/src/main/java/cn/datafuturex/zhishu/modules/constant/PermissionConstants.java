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
    /** 开放应用（AK/SK、调用范围）维护 */
    public static final String SYSTEM_OPENAPI_EDIT = "system:openapi:edit";

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

    // AI
    public static final String AI_AGENT_QUERY = "ai:agent:query";
    public static final String AI_AGENT_ADD = "ai:agent:add";
    public static final String AI_AGENT_EDIT = "ai:agent:edit";
    public static final String AI_AGENT_REMOVE = "ai:agent:remove";
    public static final String AI_AGENT_GRAPH = "ai:agent:graph";
    public static final String AI_MCP_EDIT = "ai:mcp:edit";
    public static final String AI_DOCUMENT_ADD = "ai:document:add";
    public static final String AI_DOCUMENT_EDIT = "ai:document:edit";
    public static final String AI_DOCUMENT_REMOVE = "ai:document:remove";
    public static final String AI_CATEGORY_EDIT = "ai:category:edit";
    public static final String AI_KG_SYNC = "ai:kg:sync";
    public static final String AI_QA_QUERY = "ai:qa:query";

    // 模型网关
    public static final String GATEWAY_PROVIDER_LIST = "gateway:provider:list";
    public static final String GATEWAY_PROVIDER_UPDATE = "gateway:provider:update";
    public static final String GATEWAY_MODEL_LIST = "gateway:model:list";
    public static final String GATEWAY_MODEL_UPDATE = "gateway:model:update";
    public static final String GATEWAY_QUOTA_LIST = "gateway:quota:list";
    public static final String GATEWAY_QUOTA_UPDATE = "gateway:quota:update";
    public static final String GATEWAY_USAGE_QUERY = "gateway:usage:query";
}
