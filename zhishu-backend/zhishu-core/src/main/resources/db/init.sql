-- =============================================================================
-- 知枢可集成框架 — 完整数据库初始化脚本（最终态）
-- 适用：PostgreSQL 14+ / 全新空库
-- 用法：
--   psql -U postgres -c "CREATE DATABASE zhishu_integrable_framework WITH ENCODING 'UTF8' TEMPLATE template0;"
--   psql -U postgres -d zhishu_integrable_framework -f zhishu-core/src/main/resources/db/init.sql
--   psql -U postgres -d zhishu_integrable_framework -f zhishu-core/src/main/resources/db/init_ai.sql
-- 说明：
--   1. 本脚本已合并全部 admin 相关表结构与种子数据，请先创建目标库再执行
--   2. 默认管理员：admin / admin123
--   3. AI 表请另执行 init_ai.sql（pgvector 扩展 + Agent/RAG/MCP/简报等）
-- 生成日期：2026-07-20
-- =============================================================================

SET client_encoding = 'UTF8';

-- ---------------------------------------------------------------------------
-- 0. 更新时间触发器（替代 MySQL ON UPDATE CURRENT_TIMESTAMP）
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_set_update_time()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.update_time := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------------
-- 1. 权限与系统管理
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    real_name VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    password VARCHAR(100) NOT NULL,
    role VARCHAR(50) DEFAULT 'USER',
    status SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_username UNIQUE (username)
);
COMMENT ON TABLE sys_user IS '系统用户表';

DROP TRIGGER IF EXISTS trg_sys_user_update_time ON sys_user;
CREATE TRIGGER trg_sys_user_update_time
    BEFORE UPDATE ON sys_user
    FOR EACH ROW
    EXECUTE PROCEDURE trg_set_update_time();

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(200),
    status SMALLINT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_code UNIQUE (role_code)
);
COMMENT ON TABLE sys_role IS '系统角色表';

DROP TRIGGER IF EXISTS trg_sys_role_update_time ON sys_role;
CREATE TRIGGER trg_sys_role_update_time
    BEFORE UPDATE ON sys_role
    FOR EACH ROW
    EXECUTE PROCEDURE trg_set_update_time();

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    title VARCHAR(100) NOT NULL,
    path VARCHAR(200),
    route_name VARCHAR(100),
    redirect VARCHAR(200),
    icon VARCHAR(50),
    menu_type VARCHAR(20) NOT NULL DEFAULT 'MENU',
    visible SMALLINT NOT NULL DEFAULT 1,
    requires_auth SMALLINT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    component VARCHAR(200),
    meta TEXT,
    status SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_menu IS '系统菜单表';
CREATE INDEX IF NOT EXISTS idx_sys_menu_parent_id ON sys_menu (parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_menu_route_name ON sys_menu (route_name);

DROP TRIGGER IF EXISTS trg_sys_menu_update_time ON sys_menu;
CREATE TRIGGER trg_sys_menu_update_time
    BEFORE UPDATE ON sys_menu
    FOR EACH ROW
    EXECUTE PROCEDURE trg_set_update_time();

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    CONSTRAINT uk_role_menu UNIQUE (role_id, menu_id)
);
COMMENT ON TABLE sys_role_menu IS '角色菜单关联表';
CREATE INDEX IF NOT EXISTS idx_sys_role_menu_menu_id ON sys_role_menu (menu_id);

CREATE TABLE IF NOT EXISTS sys_unit (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    unit_code VARCHAR(50),
    unit_name VARCHAR(100) NOT NULL,
    unit_type VARCHAR(50),
    region VARCHAR(100),
    address VARCHAR(200),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    sort INT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1,
    remark VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_unit_code UNIQUE (unit_code)
);
COMMENT ON TABLE sys_unit IS '单位管理表';
CREATE INDEX IF NOT EXISTS idx_sys_unit_parent_id ON sys_unit (parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_unit_unit_name ON sys_unit (unit_name);
CREATE INDEX IF NOT EXISTS idx_sys_unit_status ON sys_unit (status);

DROP TRIGGER IF EXISTS trg_sys_unit_update_time ON sys_unit;
CREATE TRIGGER trg_sys_unit_update_time
    BEFORE UPDATE ON sys_unit
    FOR EACH ROW
    EXECUTE PROCEDURE trg_set_update_time();

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT PRIMARY KEY DEFAULT 1,
    system_name VARCHAR(100) NOT NULL,
    english_title VARCHAR(100),
    system_icon VARCHAR(500),
    copyright VARCHAR(200),
    system_introduction TEXT,
    project_site VARCHAR(200),
    login_retry_limit_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    login_max_retry_attempts INT NOT NULL DEFAULT 5,
    login_lock_minutes INT NOT NULL DEFAULT 3,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_config IS '系统配置表';

DROP TRIGGER IF EXISTS trg_sys_config_update_time ON sys_config;
CREATE TRIGGER trg_sys_config_update_time
    BEFORE UPDATE ON sys_config
    FOR EACH ROW
    EXECUTE PROCEDURE trg_set_update_time();

CREATE TABLE IF NOT EXISTS sys_announcement (
    id BIGINT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    priority SMALLINT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 0,
    publish_time TIMESTAMP,
    publisher_id BIGINT,
    publisher_name VARCHAR(50),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_announcement IS '系统公告表';
CREATE INDEX IF NOT EXISTS idx_sys_announcement_status ON sys_announcement (status);
CREATE INDEX IF NOT EXISTS idx_sys_announcement_publish_time ON sys_announcement (publish_time);
CREATE INDEX IF NOT EXISTS idx_sys_announcement_create_time ON sys_announcement (create_time);

DROP TRIGGER IF EXISTS trg_sys_announcement_update_time ON sys_announcement;
CREATE TRIGGER trg_sys_announcement_update_time
    BEFORE UPDATE ON sys_announcement
    FOR EACH ROW
    EXECUTE PROCEDURE trg_set_update_time();

CREATE TABLE IF NOT EXISTS sys_announcement_read (
    id BIGINT PRIMARY KEY,
    announcement_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_announcement_user UNIQUE (announcement_id, user_id)
);
COMMENT ON TABLE sys_announcement_read IS '公告已读记录表';
CREATE INDEX IF NOT EXISTS idx_sys_announcement_read_user_id ON sys_announcement_read (user_id);

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(50),
    real_name VARCHAR(50),
    module VARCHAR(50) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    method VARCHAR(200),
    request_params TEXT,
    response_code INT,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    duration_ms INT,
    status SMALLINT NOT NULL DEFAULT 1,
    error_message VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_operation_log IS '系统操作日志表';
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_username ON sys_operation_log (username);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_module ON sys_operation_log (module);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_operation ON sys_operation_log (operation);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_create_time ON sys_operation_log (create_time);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_status ON sys_operation_log (status);

-- ---------------------------------------------------------------------------
-- 2. 种子数据
-- ---------------------------------------------------------------------------

INSERT INTO sys_user (id, username, real_name, password, role, status)
VALUES (1, 'admin', '系统管理员', '$2a$10$672KsXLZg0JGRwUiwcZdA.gdJXI05j9aemXqj0x18o6pixlWn9fZm', 'ADMIN', 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_role (id, role_code, role_name, description, status, sort) VALUES
(1, 'ADMIN', '超级管理员', '拥有全部菜单权限', 1, 1),
(2, 'USER', '普通用户', '基础查看与个人中心权限', 1, 2)
ON CONFLICT (id) DO UPDATE SET
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description;

INSERT INTO sys_config (
    id, system_name, english_title, copyright, system_introduction,
    login_retry_limit_enabled, login_max_retry_attempts, login_lock_minutes
) VALUES (
    1, '知枢可集成框架', 'ZhiShu Integrable Framework', '© 2026 知枢可集成框架 · MIT 开源',
    '一套面向企业数字化与智能化应用集成的模块化开发底座，通过统一技术架构、业务组件、伙伴 SSO 与行业扩展能力，帮助企业快速构建可集成、可扩展的应用系统。',
    FALSE, 5, 3
)
ON CONFLICT (id) DO UPDATE SET
    system_name = EXCLUDED.system_name,
    english_title = EXCLUDED.english_title,
    copyright = EXCLUDED.copyright,
    system_introduction = EXCLUDED.system_introduction;

INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status) VALUES
(1, 0, '工作台', '/home', 'Home', NULL, 'HomeFilled', 'DIRECTORY', 0, 1, 1, NULL, NULL, 0),
(11, 1, '仪表盘', '/home/dashboard', 'Dashboard', NULL, 'Odometer', 'MENU', 0, 1, 9, 'views/dashboard/Dashboard.vue', NULL, 0),
(5, 0, '账号管理', '/permission', 'Permission', '/permission/user', 'Key', 'DIRECTORY', 1, 1, 3, NULL, NULL, 1),
(61, 5, '用户管理', '/permission/user', 'User', NULL, 'User', 'MENU', 1, 1, 1, 'views/user/UserList.vue', NULL, 1),
(63, 5, '菜单管理', '/permission/menu', 'MenuManage', NULL, 'Menu', 'MENU', 1, 1, 2, 'views/system/MenuList.vue', NULL, 1),
(64, 5, '角色管理', '/permission/role', 'RoleManage', NULL, 'Avatar', 'MENU', 1, 1, 3, 'views/system/RoleList.vue', NULL, 1),
(66, 5, '单位管理', '/permission/unit', 'UnitManage', NULL, 'OfficeBuilding', 'MENU', 1, 1, 4, 'views/system/UnitList.vue', NULL, 1),
(6, 0, '系统设置', '/system', 'System', '/system/config', 'SetUp', 'DIRECTORY', 1, 1, 4, NULL, NULL, 1),
(65, 6, '参数配置', '/system/config', 'SystemConfig', NULL, 'Tools', 'MENU', 1, 1, 1, 'views/system/SystemConfig.vue', NULL, 1),
(68, 6, '操作日志', '/system/operation-log', 'OperationLog', NULL, 'Document', 'MENU', 1, 1, 2, 'views/system/OperationLogList.vue', NULL, 1),
(69, 6, '公告管理', '/system/announcement', 'AnnouncementManage', NULL, 'Notification', 'MENU', 1, 1, 3, 'views/system/AnnouncementList.vue', NULL, 1),
(67, 6, '运维监控', '/monitor/ops', 'OpsMonitor', NULL, 'Odometer', 'MENU', 1, 1, 4, 'views/system/SystemMonitor.vue', NULL, 1),
(91, 6, '后端接口', '/devtools/api', 'BackendApi', NULL, 'Document', 'MENU', 1, 1, 5, 'views/devtools/SwaggerEmbed.vue', '{"fullBleed":true,"hideTabBar":false,"hideBreadcrumb":true,"hideSecondaryAside":false}', 1),
(610, 6, '开放能力', '/system/open-api', 'OpenApiCapabilities', NULL, 'Connection', 'MENU', 1, 1, 6, 'views/system/OpenApiCapabilities.vue', '{"title":"开放能力"}', 1),
(7, 0, '个人中心', '/profile', 'Profile', '/profile/info', 'UserFilled', 'DIRECTORY', 1, 1, 9, NULL, NULL, 1),
(71, 7, '个人信息', '/profile/info', 'ProfileInfo', NULL, 'User', 'MENU', 1, 1, 1, 'views/user/UserProfile.vue', NULL, 1),
(72, 7, '修改密码', '/profile/password', 'ChangePassword', NULL, 'Lock', 'MENU', 1, 1, 2, 'views/user/ChangePassword.vue', NULL, 1),
(9001, 0, '登录', '/login', 'Login', NULL, NULL, 'PAGE', 0, 0, 0, 'views/login/Login.vue', NULL, 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    title = EXCLUDED.title,
    path = EXCLUDED.path,
    route_name = EXCLUDED.route_name,
    redirect = EXCLUDED.redirect,
    icon = EXCLUDED.icon,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    requires_auth = EXCLUDED.requires_auth,
    sort = EXCLUDED.sort,
    component = EXCLUDED.component,
    meta = EXCLUDED.meta,
    status = EXCLUDED.status;

-- 智能中心菜单（工作台 / 仪表盘已禁用；Agent 会话挂在本目录下）
INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status) VALUES
(10, 0, '智能中心', '/ai', 'AI', '/ai/chat', 'MagicStick', 'DIRECTORY', 1, 1, 2, NULL, NULL, 1),
(103, 10, 'Agent 会话', '/ai/chat', 'AIChat', NULL, 'ChatLineRound', 'MENU', 1, 1, 1, 'views/ai/AIChat.vue', '{"title":"Agent 会话"}', 1),
(109, 10, '知识检索', '/ai/qa', 'AIDocumentQA', NULL, 'Search', 'MENU', 1, 1, 2, 'views/ai/DocumentQA.vue', '{"title":"知识检索"}', 1),
(106, 10, '知识图谱', '/ai/knowledge-graph', 'AIKnowledgeGraph', NULL, 'Connection', 'MENU', 1, 1, 3, 'views/ai/KnowledgeGraph.vue', '{"title":"知识图谱"}', 1),
(101, 10, 'Agents', '/ai/agents', 'AIAgentManage', NULL, 'Cpu', 'MENU', 1, 1, 4, 'views/ai/AgentManage.vue', '{"title":"Agents 管理"}', 1),
(102, 10, '知识库', '/ai/knowledges', 'AIDocumentManage', NULL, 'FolderOpened', 'MENU', 1, 1, 5, 'views/ai/DocumentManage.vue', '{"title":"知识库"}', 1),
(104, 10, 'MCP Hub', '/ai/mcp', 'AIMcpHub', NULL, 'Link', 'MENU', 1, 1, 6, 'views/ai/McpHub.vue', '{"title":"MCP Hub"}', 1),
(105, 10, '模型设置', '/ai/model-config', 'AIModelConfig', NULL, 'SetUp', 'MENU', 1, 1, 7, 'views/ai/ModelConfig.vue', '{"title":"模型设置"}', 1),
(108, 10, '工作流编排', '/ai/agents/:id/graph', 'AIAgentGraphEditor', NULL, NULL, 'PAGE', 0, 1, 8, 'views/ai/AgentGraphEditor.vue', NULL, 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    title = EXCLUDED.title,
    path = EXCLUDED.path,
    route_name = EXCLUDED.route_name,
    redirect = EXCLUDED.redirect,
    icon = EXCLUDED.icon,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    requires_auth = EXCLUDED.requires_auth,
    sort = EXCLUDED.sort,
    component = EXCLUDED.component,
    meta = EXCLUDED.meta,
    status = EXCLUDED.status;

UPDATE sys_menu SET sort = 9, update_time = CURRENT_TIMESTAMP WHERE id = 7;

-- 清理已废弃的顶层目录（系统监控、开发工具）
DELETE FROM sys_role_menu WHERE menu_id IN (8, 9);
DELETE FROM sys_menu WHERE id IN (8, 9);

INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status) VALUES
(1101, 11, '仪表盘查询', NULL, 'home:dashboard:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 0),
(6101, 61, '用户查询', NULL, 'system:user:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(6102, 61, '用户新增', NULL, 'system:user:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(6103, 61, '用户修改', NULL, 'system:user:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1),
(6104, 61, '用户删除', NULL, 'system:user:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1),
(6105, 61, '分配角色', NULL, 'system:user:assignRole', NULL, NULL, 'BUTTON', 0, 1, 5, NULL, NULL, 1),
(6301, 63, '菜单查询', NULL, 'system:menu:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(6302, 63, '菜单新增', NULL, 'system:menu:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(6303, 63, '菜单修改', NULL, 'system:menu:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1),
(6304, 63, '菜单删除', NULL, 'system:menu:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1),
(6401, 64, '角色查询', NULL, 'system:role:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(6402, 64, '角色新增', NULL, 'system:role:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(6403, 64, '角色修改', NULL, 'system:role:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1),
(6404, 64, '角色删除', NULL, 'system:role:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1),
(6405, 64, '分配菜单权限', NULL, 'system:role:assignMenu', NULL, NULL, 'BUTTON', 0, 1, 5, NULL, NULL, 1),
(6601, 66, '单位查询', NULL, 'system:unit:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(6602, 66, '单位新增', NULL, 'system:unit:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(6603, 66, '单位修改', NULL, 'system:unit:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1),
(6604, 66, '单位删除', NULL, 'system:unit:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1),
(6501, 65, '参数配置查询', NULL, 'system:config:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(6502, 65, '参数配置修改', NULL, 'system:config:edit', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(6801, 68, '操作日志查询', NULL, 'system:operlog:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(6901, 69, '公告查询', NULL, 'system:announcement:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(6902, 69, '公告新增', NULL, 'system:announcement:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(6903, 69, '公告修改', NULL, 'system:announcement:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1),
(6904, 69, '公告删除', NULL, 'system:announcement:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1),
(6905, 69, '公告发布', NULL, 'system:announcement:publish', NULL, NULL, 'BUTTON', 0, 1, 5, NULL, NULL, 1),
(6701, 67, '运维监控查询', NULL, 'system:monitor:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(9101, 91, '后端接口查询', NULL, 'devtools:api:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(7101, 71, '个人信息查询', NULL, 'profile:info:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(7201, 72, '修改密码', NULL, 'profile:password:edit', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(10101, 101, '智能体查询', NULL, 'ai:agent:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(10102, 101, '智能体新增', NULL, 'ai:agent:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(10103, 101, '智能体修改', NULL, 'ai:agent:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1),
(10104, 101, '智能体删除', NULL, 'ai:agent:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1),
(10105, 101, '工作流编排', NULL, 'ai:agent:graph', NULL, NULL, 'BUTTON', 0, 1, 5, NULL, NULL, 1),
(10401, 104, 'MCP 编辑', NULL, 'ai:mcp:edit', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(10601, 106, '知识图谱同步', NULL, 'ai:kg:sync', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(10901, 109, '知识检索查询', NULL, 'ai:qa:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    route_name = EXCLUDED.route_name,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    parent_id = EXCLUDED.parent_id,
    status = EXCLUDED.status;

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10000 + m.id, 1, m.id FROM sys_menu m WHERE m.menu_type IN ('DIRECTORY', 'MENU', 'PAGE')
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 100000 + m.id, 1, m.id FROM sys_menu m WHERE m.menu_type = 'BUTTON'
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;

INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
(2103, 2, 103),
(2010, 2, 10),
(2109, 2, 109),
(2106, 2, 106),
(2005, 2, 7),
(2071, 2, 71),
(2072, 2, 72),
(27101, 2, 7101),
(27201, 2, 7201),
(210901, 2, 10901)
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;

-- =============================================================================
-- 初始化完成
-- 表：sys_user / sys_role / sys_menu / sys_role_menu / sys_unit
--      sys_config / sys_announcement / sys_announcement_read / sys_operation_log
-- =============================================================================
