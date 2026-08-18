-- =============================================================================
-- 云起应用平台 — 完整数据库初始化脚本（最终态）
-- 适用：MySQL 8.0+ / 全新空库
-- 用法：mysql -u root -p < yqap-core/src/main/resources/db/init.sql
-- 说明：
--   1. 本脚本已合并全部 admin 相关 migration 的最终表结构与种子数据，新库无需再跑其它 SQL
--   2. 默认管理员：admin / admin123
-- 生成日期：2026-07-20
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `yunqi_admin` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `yunqi_admin`;

-- ---------------------------------------------------------------------------
-- 1. 权限与系统管理
-- ---------------------------------------------------------------------------

-- 系统用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `real_name` VARCHAR(50) COMMENT '真实姓名',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `password` VARCHAR(100) NOT NULL COMMENT '密码(BCrypt加密)',
    `role` VARCHAR(50) DEFAULT 'USER' COMMENT '角色编码（关联 sys_role.role_code）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '用户状态（1-正常，0-禁用）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 系统角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码（唯一，如 ADMIN）',
    `role_name` VARCHAR(100) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(200) COMMENT '角色描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1-启用，0-禁用）',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 系统菜单表（含 DIRECTORY/MENU/PAGE/BUTTON）
CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id` BIGINT NOT NULL COMMENT '主键ID（与前端路由 id 对齐）',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父菜单ID，0 表示根节点',
    `title` VARCHAR(100) NOT NULL COMMENT '菜单标题',
    `path` VARCHAR(200) COMMENT '前端路由路径',
    `route_name` VARCHAR(100) COMMENT 'Vue Router name，BUTTON 时为权限码',
    `redirect` VARCHAR(200) COMMENT '重定向路径',
    `icon` VARCHAR(50) COMMENT '图标（Element Plus Icons 组件名）',
    `menu_type` VARCHAR(20) NOT NULL DEFAULT 'MENU' COMMENT '菜单类型：DIRECTORY/MENU/PAGE/BUTTON',
    `visible` TINYINT NOT NULL DEFAULT 1 COMMENT '是否在侧边栏展示（1-是，0-否）',
    `requires_auth` TINYINT NOT NULL DEFAULT 1 COMMENT '是否需要登录（1-是，0-否）',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '同级排序，越小越靠前',
    `component` VARCHAR(200) COMMENT '前端组件路径',
    `meta` JSON COMMENT '扩展元数据',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1-启用，0-禁用）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_route_name` (`route_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单表';

-- 角色菜单关联表
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_role_menu` (`role_id`, `menu_id`),
    INDEX `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- 单位管理表
CREATE TABLE IF NOT EXISTS `sys_unit` (
    `id` BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父单位ID，0 表示根节点',
    `unit_code` VARCHAR(50) COMMENT '单位编码（未填写时系统自动生成，唯一）',
    `unit_name` VARCHAR(100) NOT NULL COMMENT '单位名称',
    `unit_type` VARCHAR(50) COMMENT '单位类型（主管单位/管理单位/运维单位等）',
    `region` VARCHAR(100) COMMENT '所属区域',
    `address` VARCHAR(200) COMMENT '单位地址',
    `contact_person` VARCHAR(50) COMMENT '联系人',
    `contact_phone` VARCHAR(20) COMMENT '联系电话',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '同级排序，越小越靠前',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1-启用，0-停用）',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_unit_code` (`unit_code`),
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_unit_name` (`unit_name`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单位管理表';

-- 系统配置表（单例 id=1）
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id` BIGINT NOT NULL DEFAULT 1 COMMENT '固定为1，单例配置',
    `system_name` VARCHAR(100) NOT NULL COMMENT '系统名称',
    `english_title` VARCHAR(100) COMMENT '英文标题',
    `system_icon` VARCHAR(500) COMMENT '系统图标URL',
    `copyright` VARCHAR(200) COMMENT '版权信息',
    `system_introduction` TEXT COMMENT '系统介绍信息',
    `project_site` VARCHAR(200) COMMENT '项目地',
    `login_retry_limit_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否开启登录重试次数限制',
    `login_max_retry_attempts` INT NOT NULL DEFAULT 5 COMMENT '允许用户名密码错误次数',
    `login_lock_minutes` INT NOT NULL DEFAULT 3 COMMENT '超过失败次数后锁定分钟数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 系统公告表
CREATE TABLE IF NOT EXISTS `sys_announcement` (
    `id` BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
    `title` VARCHAR(200) NOT NULL COMMENT '公告标题',
    `content` TEXT NOT NULL COMMENT '公告内容',
    `priority` TINYINT NOT NULL DEFAULT 0 COMMENT '优先级（0-普通，1-重要，2-紧急）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态（0-草稿，1-已发布，2-已撤回）',
    `publish_time` DATETIME COMMENT '发布时间',
    `publisher_id` BIGINT COMMENT '发布人ID',
    `publisher_name` VARCHAR(50) COMMENT '发布人姓名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_publish_time` (`publish_time`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';

-- 公告已读记录表
CREATE TABLE IF NOT EXISTS `sys_announcement_read` (
    `id` BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
    `announcement_id` BIGINT NOT NULL COMMENT '公告ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `read_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_announcement_user` (`announcement_id`, `user_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告已读记录表';

-- 系统操作日志表
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
    `id` BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
    `user_id` BIGINT COMMENT '操作用户ID',
    `username` VARCHAR(50) COMMENT '操作用户名',
    `real_name` VARCHAR(50) COMMENT '操作用户真实姓名',
    `module` VARCHAR(50) NOT NULL COMMENT '模块名称',
    `operation` VARCHAR(50) NOT NULL COMMENT '操作类型（CREATE/UPDATE/DELETE/QUERY/LOGIN 等）',
    `method` VARCHAR(200) COMMENT '请求方法（HTTP方法 + URI）',
    `request_params` TEXT COMMENT '请求参数（JSON）',
    `response_code` INT COMMENT 'HTTP响应码',
    `ip_address` VARCHAR(50) COMMENT '客户端IP',
    `user_agent` VARCHAR(500) COMMENT 'User-Agent',
    `duration_ms` INT COMMENT '耗时（毫秒）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（1-成功，0-失败）',
    `error_message` VARCHAR(500) COMMENT '失败原因',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    INDEX `idx_username` (`username`),
    INDEX `idx_module` (`module`),
    INDEX `idx_operation` (`operation`),
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志表';

-- ---------------------------------------------------------------------------
-- 2. 种子数据
-- ---------------------------------------------------------------------------

-- 2.1 默认管理员（密码: admin123）
INSERT INTO `sys_user` (`id`, `username`, `real_name`, `password`, `role`, `status`)
VALUES (1, 'admin', '系统管理员', '$2a$10$672KsXLZg0JGRwUiwcZdA.gdJXI05j9aemXqj0x18o6pixlWn9fZm', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE `username` = `username`;

-- 2.2 角色
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `status`, `sort`) VALUES
(1, 'ADMIN', '超级管理员', '拥有全部菜单权限', 1, 1),
(2, 'USER', '普通用户', '基础查看与个人中心权限', 1, 2)
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `description` = VALUES(`description`);

-- 2.3 系统配置
INSERT INTO `sys_config` (
    `id`, `system_name`, `english_title`, `copyright`, `system_introduction`,
    `login_retry_limit_enabled`, `login_max_retry_attempts`, `login_lock_minutes`
) VALUES (
    1, '云起应用平台', 'YunQi Application Platform', '© 2026 云起应用平台 · MIT 开源',
    '一套面向企业数字化应用建设的模块化开发基础平台，通过统一技术架构、业务组件、AI能力和行业扩展能力，帮助企业快速构建智能化应用系统。',
    0, 5, 3
)
ON DUPLICATE KEY UPDATE
    `system_name` = VALUES(`system_name`),
    `english_title` = VALUES(`english_title`),
    `copyright` = VALUES(`copyright`),
    `system_introduction` = VALUES(`system_introduction`);

-- 2.4 菜单树（DIRECTORY / MENU / PAGE）
INSERT INTO `sys_menu` (`id`, `parent_id`, `title`, `path`, `route_name`, `redirect`, `icon`, `menu_type`, `visible`, `requires_auth`, `sort`, `component`, `meta`, `status`) VALUES
-- 主页
(1, 0, '主页', '/home', 'Home', '/home/dashboard', 'HomeFilled', 'DIRECTORY', 1, 1, 1, NULL, NULL, 1),
(11, 1, '仪表盘', '/home/dashboard', 'Dashboard', NULL, 'Odometer', 'MENU', 1, 1, 1, 'views/dashboard/Dashboard.vue', NULL, 1),
-- 权限管理
(5, 0, '权限管理', '/permission', 'Permission', '/permission/user', 'Key', 'DIRECTORY', 1, 1, 2, NULL, NULL, 1),
(61, 5, '用户管理', '/permission/user', 'User', NULL, 'User', 'MENU', 1, 1, 1, 'views/user/UserList.vue', NULL, 1),
(63, 5, '菜单管理', '/permission/menu', 'MenuManage', NULL, 'Menu', 'MENU', 1, 1, 2, 'views/system/MenuList.vue', NULL, 1),
(64, 5, '角色管理', '/permission/role', 'RoleManage', NULL, 'Avatar', 'MENU', 1, 1, 3, 'views/system/RoleList.vue', NULL, 1),
(66, 5, '单位管理', '/permission/unit', 'UnitManage', NULL, 'OfficeBuilding', 'MENU', 1, 1, 4, 'views/system/UnitList.vue', NULL, 1),
-- 系统管理
(6, 0, '系统管理', '/system', 'System', '/system/config', 'Setting', 'DIRECTORY', 1, 1, 3, NULL, NULL, 1),
(65, 6, '系统设置', '/system/config', 'SystemConfig', NULL, 'Tools', 'MENU', 1, 1, 1, 'views/system/SystemConfig.vue', NULL, 1),
(68, 6, '操作日志', '/system/operation-log', 'OperationLog', NULL, 'Document', 'MENU', 1, 1, 2, 'views/system/OperationLogList.vue', NULL, 1),
(69, 6, '公告管理', '/system/announcement', 'AnnouncementManage', NULL, 'Notification', 'MENU', 1, 1, 3, 'views/system/AnnouncementList.vue', NULL, 1),
-- 系统监控
(8, 0, '系统监控', '/monitor', 'Monitor', '/monitor/ops', 'Monitor', 'DIRECTORY', 1, 1, 4, NULL, NULL, 1),
(67, 8, '运维监控', '/monitor/ops', 'OpsMonitor', NULL, 'Odometer', 'MENU', 1, 1, 1, 'views/system/SystemMonitor.vue', NULL, 1),
-- 开发工具
(9, 0, '开发工具', '/devtools', 'DevTools', '/devtools/api', 'Cpu', 'DIRECTORY', 1, 1, 5, NULL, NULL, 1),
(91, 9, '后端接口', '/devtools/api', 'BackendApi', NULL, 'Document', 'MENU', 1, 1, 1, 'views/devtools/SwaggerEmbed.vue', '{"fullBleed":true,"hideTabBar":false,"hideBreadcrumb":true,"hideSecondaryAside":false}', 1),
-- 个人中心
(7, 0, '个人中心', '/profile', 'Profile', '/profile/info', 'UserFilled', 'DIRECTORY', 1, 1, 6, NULL, NULL, 1),
(71, 7, '个人信息', '/profile/info', 'ProfileInfo', NULL, 'User', 'MENU', 1, 1, 1, 'views/user/UserProfile.vue', NULL, 1),
(72, 7, '修改密码', '/profile/password', 'ChangePassword', NULL, 'Lock', 'MENU', 1, 1, 2, 'views/user/ChangePassword.vue', NULL, 1),
-- 登录页（隐藏）
(9001, 0, '登录', '/login', 'Login', NULL, NULL, 'PAGE', 0, 0, 0, 'views/login/Login.vue', NULL, 1)
ON DUPLICATE KEY UPDATE
    `parent_id` = VALUES(`parent_id`),
    `title` = VALUES(`title`),
    `path` = VALUES(`path`),
    `route_name` = VALUES(`route_name`),
    `redirect` = VALUES(`redirect`),
    `icon` = VALUES(`icon`),
    `menu_type` = VALUES(`menu_type`),
    `visible` = VALUES(`visible`),
    `requires_auth` = VALUES(`requires_auth`),
    `sort` = VALUES(`sort`),
    `component` = VALUES(`component`),
    `meta` = VALUES(`meta`),
    `status` = VALUES(`status`);

-- 2.5 按钮权限（menu_type = BUTTON，route_name = 权限码）
INSERT INTO `sys_menu` (`id`, `parent_id`, `title`, `path`, `route_name`, `redirect`, `icon`, `menu_type`, `visible`, `requires_auth`, `sort`, `component`, `meta`, `status`) VALUES
-- 主页
(1101, 11, '仪表盘查询', NULL, 'home:dashboard:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
-- 权限管理
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
-- 系统管理
(6501, 65, '系统设置查询', NULL, 'system:config:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(6502, 65, '系统设置修改', NULL, 'system:config:edit', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(6801, 68, '操作日志查询', NULL, 'system:operlog:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(6901, 69, '公告查询', NULL, 'system:announcement:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(6902, 69, '公告新增', NULL, 'system:announcement:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(6903, 69, '公告修改', NULL, 'system:announcement:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1),
(6904, 69, '公告删除', NULL, 'system:announcement:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1),
(6905, 69, '公告发布', NULL, 'system:announcement:publish', NULL, NULL, 'BUTTON', 0, 1, 5, NULL, NULL, 1),
-- 系统监控
(6701, 67, '运维监控查询', NULL, 'system:monitor:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
-- 开发工具
(9101, 91, '后端接口查询', NULL, 'devtools:api:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
-- 个人中心
(7101, 71, '个人信息查询', NULL, 'profile:info:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(7201, 72, '修改密码', NULL, 'profile:password:edit', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1)
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`),
    `route_name` = VALUES(`route_name`),
    `menu_type` = VALUES(`menu_type`),
    `visible` = VALUES(`visible`),
    `parent_id` = VALUES(`parent_id`);

-- 2.6 超级管理员：全部菜单 + 全部 BUTTON
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 10000 + m.id, 1, m.id FROM `sys_menu` m WHERE m.menu_type IN ('DIRECTORY', 'MENU', 'PAGE')
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`)
SELECT 100000 + m.id, 1, m.id FROM `sys_menu` m WHERE m.menu_type = 'BUTTON'
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

-- 2.7 普通用户：主页、个人中心及对应查询权限
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`) VALUES
(2001, 2, 1),
(2011, 2, 11),
(2005, 2, 7),
(2071, 2, 71),
(2072, 2, 72),
(21101, 2, 1101),
(27101, 2, 7101),
(27201, 2, 7201)
ON DUPLICATE KEY UPDATE `menu_id` = VALUES(`menu_id`);

-- =============================================================================
-- 初始化完成
-- 表：sys_user / sys_role / sys_menu / sys_role_menu / sys_unit
--      sys_config / sys_announcement / sys_announcement_read / sys_operation_log
-- =============================================================================
