-- =============================================================================
-- 基础功能数据库脚本（含权限：菜单 / 角色 / 角色菜单 / 用户 / 单位 / 系统配置 / 公告 / 操作日志）
--
-- 来源：以开发库实际结构导出后幂等化（CREATE ... IF NOT EXISTS、约束内联、索引 IF NOT EXISTS），
--       与 MyBatis-Plus 实体（cn.datafuturex.zhishu.modules.*、biz.*）保持一致。
-- 幂等：可重复执行；种子数据使用 ON CONFLICT DO NOTHING。
-- 执行：psql -f db/init.sql（手工执行；含触发器函数体，不能被启动器按分号分隔加载）
-- 注意：种子数据内的初始管理员口令为开发默认值，首次登录后请立即修改。
-- =============================================================================

-- ---------- 表结构与字段备注（每表 DDL 后紧跟 COMMENT）----------
CREATE TABLE IF NOT EXISTS public.sys_announcement (
    id bigint NOT NULL,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    priority smallint DEFAULT 0 NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    publish_time timestamp without time zone,
    publisher_id bigint,
    publisher_name character varying(50),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT sys_announcement_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE public.sys_announcement IS '系统公告';
COMMENT ON COLUMN public.sys_announcement.id IS '主键';
COMMENT ON COLUMN public.sys_announcement.title IS '公告标题';
COMMENT ON COLUMN public.sys_announcement.content IS '公告正文（Markdown）';
COMMENT ON COLUMN public.sys_announcement.priority IS '优先级：数值越大越靠前';
COMMENT ON COLUMN public.sys_announcement.status IS '状态：0=草稿，1=已发布，2=已下线';
COMMENT ON COLUMN public.sys_announcement.publish_time IS '发布时间';
COMMENT ON COLUMN public.sys_announcement.publisher_id IS '发布人用户 id';
COMMENT ON COLUMN public.sys_announcement.publisher_name IS '发布人姓名（冗余快照）';
COMMENT ON COLUMN public.sys_announcement.create_time IS '创建时间';
COMMENT ON COLUMN public.sys_announcement.update_time IS '更新时间';
CREATE TABLE IF NOT EXISTS public.sys_announcement_read (
    id bigint NOT NULL,
    announcement_id bigint NOT NULL,
    user_id bigint NOT NULL,
    read_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT sys_announcement_read_pkey PRIMARY KEY (id),
    CONSTRAINT uk_announcement_user UNIQUE (announcement_id, user_id)
);

COMMENT ON TABLE public.sys_announcement_read IS '公告已读记录';
COMMENT ON COLUMN public.sys_announcement_read.id IS '主键';
COMMENT ON COLUMN public.sys_announcement_read.announcement_id IS '公告 id（sys_announcement.id）';
COMMENT ON COLUMN public.sys_announcement_read.user_id IS '阅读用户 id';
COMMENT ON COLUMN public.sys_announcement_read.read_time IS '首次阅读时间';
CREATE TABLE IF NOT EXISTS public.sys_config (
    id bigint DEFAULT 1 NOT NULL,
    system_name character varying(100) NOT NULL,
    english_title character varying(100),
    system_icon character varying(500),
    copyright character varying(200),
    system_introduction text,
    project_site character varying(200),
    login_retry_limit_enabled boolean DEFAULT false NOT NULL,
    login_max_retry_attempts integer DEFAULT 5 NOT NULL,
    login_lock_minutes integer DEFAULT 3 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    chat_enable_rag_default boolean DEFAULT false NOT NULL,
    CONSTRAINT sys_config_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE public.sys_config IS '系统配置（单行表，id 固定为 1）';
COMMENT ON COLUMN public.sys_config.id IS '主键（固定 1，单行配置）';
COMMENT ON COLUMN public.sys_config.system_name IS '系统名称';
COMMENT ON COLUMN public.sys_config.english_title IS '英文标题';
COMMENT ON COLUMN public.sys_config.system_icon IS '系统图标（URL 或 data URI）';
COMMENT ON COLUMN public.sys_config.copyright IS '版权信息';
COMMENT ON COLUMN public.sys_config.system_introduction IS '系统简介';
COMMENT ON COLUMN public.sys_config.project_site IS '项目站点地址';
COMMENT ON COLUMN public.sys_config.login_retry_limit_enabled IS '是否启用登录失败限制：true=启用';
COMMENT ON COLUMN public.sys_config.login_max_retry_attempts IS '登录失败次数上限（达到后锁定）';
COMMENT ON COLUMN public.sys_config.login_lock_minutes IS '登录锁定分钟数';
COMMENT ON COLUMN public.sys_config.create_time IS '创建时间';
COMMENT ON COLUMN public.sys_config.update_time IS '更新时间';
COMMENT ON COLUMN public.sys_config.chat_enable_rag_default IS 'AI 对话是否默认开启 RAG 检索';
CREATE TABLE IF NOT EXISTS public.sys_menu (
    id bigint NOT NULL,
    parent_id bigint DEFAULT 0 NOT NULL,
    title character varying(100) NOT NULL,
    path character varying(200),
    route_name character varying(100),
    redirect character varying(200),
    icon character varying(50),
    menu_type character varying(20) DEFAULT 'MENU'::character varying NOT NULL,
    visible smallint DEFAULT 1 NOT NULL,
    requires_auth smallint DEFAULT 1 NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    component character varying(200),
    meta text,
    status smallint DEFAULT 1 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT sys_menu_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE public.sys_menu IS '菜单与按钮权限（BUTTON 行的 route_name 即权限码）';
COMMENT ON COLUMN public.sys_menu.id IS '主键';
COMMENT ON COLUMN public.sys_menu.parent_id IS '父节点 id（顶级为 0）';
COMMENT ON COLUMN public.sys_menu.title IS '显示标题';
COMMENT ON COLUMN public.sys_menu.path IS '前端路由路径（按钮行为空）';
COMMENT ON COLUMN public.sys_menu.route_name IS '路由名；menu_type=BUTTON 时存放权限码，如 ai:agent:add';
COMMENT ON COLUMN public.sys_menu.redirect IS '访问该路由时的重定向目标';
COMMENT ON COLUMN public.sys_menu.icon IS '菜单图标名称';
COMMENT ON COLUMN public.sys_menu.menu_type IS '类型：MENU=菜单目录，PAGE=页面，BUTTON=按钮权限';
COMMENT ON COLUMN public.sys_menu.visible IS '是否在侧边栏显示：1=显示，0=隐藏';
COMMENT ON COLUMN public.sys_menu.requires_auth IS '是否需要登录：1=需要，0=匿名可访问';
COMMENT ON COLUMN public.sys_menu.sort IS '同级排序值（升序）';
COMMENT ON COLUMN public.sys_menu.component IS '前端组件路径（按钮行为空）';
COMMENT ON COLUMN public.sys_menu.meta IS '额外元数据（JSON，如 keepAlive、权限说明）';
COMMENT ON COLUMN public.sys_menu.status IS '状态：1=启用，0=停用（停用后不参与鉴权）';
COMMENT ON COLUMN public.sys_menu.create_time IS '创建时间';
COMMENT ON COLUMN public.sys_menu.update_time IS '更新时间';
CREATE TABLE IF NOT EXISTS public.sys_operation_log (
    id bigint NOT NULL,
    user_id bigint,
    username character varying(50),
    real_name character varying(50),
    module character varying(50) NOT NULL,
    operation character varying(50) NOT NULL,
    method character varying(200),
    request_params text,
    response_code integer,
    ip_address character varying(50),
    user_agent character varying(500),
    duration_ms integer,
    status smallint DEFAULT 1 NOT NULL,
    error_message character varying(500),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT sys_operation_log_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE public.sys_operation_log IS '操作日志（按月分表：sys_operation_log_YYYYMM）';
COMMENT ON COLUMN public.sys_operation_log.id IS '主键';
COMMENT ON COLUMN public.sys_operation_log.user_id IS '操作用户 id（匿名时为空）';
COMMENT ON COLUMN public.sys_operation_log.username IS '操作用户登录名';
COMMENT ON COLUMN public.sys_operation_log.real_name IS '操作用户姓名';
COMMENT ON COLUMN public.sys_operation_log.module IS '业务模块标识';
COMMENT ON COLUMN public.sys_operation_log.operation IS '操作描述';
COMMENT ON COLUMN public.sys_operation_log.method IS 'HTTP 方法';
COMMENT ON COLUMN public.sys_operation_log.request_params IS '请求参数（截断）';
COMMENT ON COLUMN public.sys_operation_log.response_code IS '响应码';
COMMENT ON COLUMN public.sys_operation_log.ip_address IS '客户端 IP';
COMMENT ON COLUMN public.sys_operation_log.user_agent IS 'User-Agent（截断）';
COMMENT ON COLUMN public.sys_operation_log.duration_ms IS '耗时（毫秒）';
COMMENT ON COLUMN public.sys_operation_log.status IS '状态：1=成功，0=失败';
COMMENT ON COLUMN public.sys_operation_log.error_message IS '失败原因（截断）';
COMMENT ON COLUMN public.sys_operation_log.create_time IS '发生时间';
CREATE TABLE IF NOT EXISTS public.sys_role (
    id bigint NOT NULL,
    role_code character varying(50) NOT NULL,
    role_name character varying(100) NOT NULL,
    description character varying(200),
    status smallint DEFAULT 1 NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT sys_role_pkey PRIMARY KEY (id),
    CONSTRAINT uk_role_code UNIQUE (role_code)
);

COMMENT ON TABLE public.sys_role IS '角色';
COMMENT ON COLUMN public.sys_role.id IS '主键';
COMMENT ON COLUMN public.sys_role.role_code IS '角色编码（与 sys_user.role 对应）';
COMMENT ON COLUMN public.sys_role.role_name IS '角色名称';
COMMENT ON COLUMN public.sys_role.description IS '角色描述';
COMMENT ON COLUMN public.sys_role.status IS '状态：1=启用，0=停用';
COMMENT ON COLUMN public.sys_role.sort IS '排序值（升序）';
COMMENT ON COLUMN public.sys_role.create_time IS '创建时间';
COMMENT ON COLUMN public.sys_role.update_time IS '更新时间';
CREATE TABLE IF NOT EXISTS public.sys_role_menu (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    CONSTRAINT sys_role_menu_pkey PRIMARY KEY (id),
    CONSTRAINT uk_role_menu UNIQUE (role_id, menu_id)
);

COMMENT ON TABLE public.sys_role_menu IS '角色-菜单/按钮授权关系';
COMMENT ON COLUMN public.sys_role_menu.id IS '主键';
COMMENT ON COLUMN public.sys_role_menu.role_id IS '角色 id（sys_role.id）';
COMMENT ON COLUMN public.sys_role_menu.menu_id IS '菜单或按钮 id（sys_menu.id），授权即获得其权限码';
CREATE TABLE IF NOT EXISTS public.sys_unit (
    id bigint NOT NULL,
    parent_id bigint DEFAULT 0 NOT NULL,
    unit_code character varying(50),
    unit_name character varying(100) NOT NULL,
    unit_type character varying(50),
    region character varying(100),
    address character varying(200),
    contact_person character varying(50),
    contact_phone character varying(20),
    sort integer DEFAULT 0 NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    remark character varying(500),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT sys_unit_pkey PRIMARY KEY (id),
    CONSTRAINT uk_unit_code UNIQUE (unit_code)
);

COMMENT ON TABLE public.sys_unit IS '组织单位';
COMMENT ON COLUMN public.sys_unit.id IS '主键';
COMMENT ON COLUMN public.sys_unit.parent_id IS '上级单位 id（顶级为空）';
COMMENT ON COLUMN public.sys_unit.unit_code IS '单位编码';
COMMENT ON COLUMN public.sys_unit.unit_name IS '单位名称';
COMMENT ON COLUMN public.sys_unit.unit_type IS '单位类型（如 GROUP/COMPANY/DEPARTMENT）';
COMMENT ON COLUMN public.sys_unit.region IS '所属区域';
COMMENT ON COLUMN public.sys_unit.address IS '地址';
COMMENT ON COLUMN public.sys_unit.contact_person IS '联系人';
COMMENT ON COLUMN public.sys_unit.contact_phone IS '联系电话';
COMMENT ON COLUMN public.sys_unit.sort IS '同级排序值（升序）';
COMMENT ON COLUMN public.sys_unit.status IS '状态：1=启用，0=停用';
COMMENT ON COLUMN public.sys_unit.remark IS '备注';
COMMENT ON COLUMN public.sys_unit.create_time IS '创建时间';
COMMENT ON COLUMN public.sys_unit.update_time IS '更新时间';
CREATE TABLE IF NOT EXISTS public.sys_user (
    id bigint NOT NULL,
    username character varying(50) NOT NULL,
    real_name character varying(50),
    email character varying(100),
    phone character varying(20),
    password character varying(100) NOT NULL,
    role character varying(50) DEFAULT 'USER'::character varying,
    status smallint DEFAULT 1 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT sys_user_pkey PRIMARY KEY (id),
    CONSTRAINT uk_username UNIQUE (username)
);

COMMENT ON TABLE public.sys_user IS '系统用户';
COMMENT ON COLUMN public.sys_user.id IS '主键';
COMMENT ON COLUMN public.sys_user.username IS '登录名（全局唯一）';
COMMENT ON COLUMN public.sys_user.real_name IS '姓名';
COMMENT ON COLUMN public.sys_user.email IS '邮箱';
COMMENT ON COLUMN public.sys_user.phone IS '手机号';
COMMENT ON COLUMN public.sys_user.password IS '口令哈希（BCrypt）';
COMMENT ON COLUMN public.sys_user.role IS '角色编码（ADMIN=管理员，USER=普通用户）';
COMMENT ON COLUMN public.sys_user.status IS '状态：1=启用，0=停用';
COMMENT ON COLUMN public.sys_user.create_time IS '创建时间';
COMMENT ON COLUMN public.sys_user.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_sys_announcement_create_time ON public.sys_announcement USING btree (create_time);
CREATE INDEX IF NOT EXISTS idx_sys_announcement_publish_time ON public.sys_announcement USING btree (publish_time);
CREATE INDEX IF NOT EXISTS idx_sys_announcement_read_user_id ON public.sys_announcement_read USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_announcement_status ON public.sys_announcement USING btree (status);
CREATE INDEX IF NOT EXISTS idx_sys_menu_parent_id ON public.sys_menu USING btree (parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_menu_route_name ON public.sys_menu USING btree (route_name);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_create_time ON public.sys_operation_log USING btree (create_time);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_module ON public.sys_operation_log USING btree (module);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_operation ON public.sys_operation_log USING btree (operation);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_status ON public.sys_operation_log USING btree (status);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_username ON public.sys_operation_log USING btree (username);
CREATE INDEX IF NOT EXISTS idx_sys_role_menu_menu_id ON public.sys_role_menu USING btree (menu_id);
CREATE INDEX IF NOT EXISTS idx_sys_unit_parent_id ON public.sys_unit USING btree (parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_unit_status ON public.sys_unit USING btree (status);
CREATE INDEX IF NOT EXISTS idx_sys_unit_unit_name ON public.sys_unit USING btree (unit_name);

-- ---------- update_time 自动维护触发器 ----------
-- 自动维护 update_time（与应用层显式赋值并存，作兜底）
CREATE OR REPLACE FUNCTION trg_set_update_time() RETURNS trigger AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_sys_user_update_time ON sys_user;
CREATE TRIGGER trg_sys_user_update_time BEFORE UPDATE ON sys_user FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();
DROP TRIGGER IF EXISTS trg_sys_role_update_time ON sys_role;
CREATE TRIGGER trg_sys_role_update_time BEFORE UPDATE ON sys_role FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();
DROP TRIGGER IF EXISTS trg_sys_menu_update_time ON sys_menu;
CREATE TRIGGER trg_sys_menu_update_time BEFORE UPDATE ON sys_menu FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();
DROP TRIGGER IF EXISTS trg_sys_unit_update_time ON sys_unit;
CREATE TRIGGER trg_sys_unit_update_time BEFORE UPDATE ON sys_unit FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();
DROP TRIGGER IF EXISTS trg_sys_config_update_time ON sys_config;
CREATE TRIGGER trg_sys_config_update_time BEFORE UPDATE ON sys_config FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();
DROP TRIGGER IF EXISTS trg_sys_announcement_update_time ON sys_announcement;
CREATE TRIGGER trg_sys_announcement_update_time BEFORE UPDATE ON sys_announcement FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();

-- ---------- 种子数据（结构/配置类，幂等）----------
SELECT pg_catalog.set_config('search_path', '', false);
INSERT INTO public.sys_config (id, system_name, english_title, system_icon, copyright, system_introduction, project_site, login_retry_limit_enabled, login_max_retry_attempts, login_lock_minutes, create_time, update_time, chat_enable_rag_default) VALUES (1, '知枢可集成智能体框架', 'ZhiShu Integrable Framework', NULL, '© 2026 知枢可集成框架 · MIT 开源', '一套面向企业数字化与智能化应用集成的模块化开发底座，通过统一技术架构、业务组件、伙伴 SSO 与行业扩展能力，帮助企业快速构建可集成、可扩展的应用系统。', NULL, false, 5, 3, '2026-08-19 17:23:56.611343', '2026-10-22 10:45:47.354976', false) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (61, 5, '用户管理', '/permission/user', 'User', NULL, 'User', 'MENU', 1, 1, 1, 'views/user/UserList.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-19 17:24:05.741707') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (63, 5, '菜单管理', '/permission/menu', 'MenuManage', NULL, 'Menu', 'MENU', 1, 1, 2, 'views/system/MenuList.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-19 17:24:05.741707') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (64, 5, '角色管理', '/permission/role', 'RoleManage', NULL, 'Avatar', 'MENU', 1, 1, 3, 'views/system/RoleList.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-19 17:24:05.741707') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (66, 5, '单位管理', '/permission/unit', 'UnitManage', NULL, 'OfficeBuilding', 'MENU', 1, 1, 4, 'views/system/UnitList.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-19 17:24:05.741707') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (71, 7, '个人信息', '/profile/info', 'ProfileInfo', NULL, 'User', 'MENU', 1, 1, 1, 'views/user/UserProfile.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-19 17:24:05.741707') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (72, 7, '修改密码', '/profile/password', 'ChangePassword', NULL, 'Lock', 'MENU', 1, 1, 2, 'views/user/ChangePassword.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-19 17:24:05.741707') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (9001, 0, '登录', '/login', 'Login', NULL, NULL, 'PAGE', 0, 0, 0, 'views/login/Login.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-19 17:24:05.741707') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (1101, 11, '仪表盘查询', NULL, 'home:dashboard:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6101, 61, '用户查询', NULL, 'system:user:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6102, 61, '用户新增', NULL, 'system:user:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6103, 61, '用户修改', NULL, 'system:user:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6104, 61, '用户删除', NULL, 'system:user:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6105, 61, '分配角色', NULL, 'system:user:assignRole', NULL, NULL, 'BUTTON', 0, 1, 5, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6301, 63, '菜单查询', NULL, 'system:menu:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6302, 63, '菜单新增', NULL, 'system:menu:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6303, 63, '菜单修改', NULL, 'system:menu:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6304, 63, '菜单删除', NULL, 'system:menu:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6401, 64, '角色查询', NULL, 'system:role:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6402, 64, '角色新增', NULL, 'system:role:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6403, 64, '角色修改', NULL, 'system:role:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6404, 64, '角色删除', NULL, 'system:role:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6405, 64, '分配菜单权限', NULL, 'system:role:assignMenu', NULL, NULL, 'BUTTON', 0, 1, 5, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6601, 66, '单位查询', NULL, 'system:unit:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6602, 66, '单位新增', NULL, 'system:unit:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6603, 66, '单位修改', NULL, 'system:unit:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6604, 66, '单位删除', NULL, 'system:unit:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6801, 68, '操作日志查询', NULL, 'system:operlog:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6901, 69, '公告查询', NULL, 'system:announcement:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6902, 69, '公告新增', NULL, 'system:announcement:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6903, 69, '公告修改', NULL, 'system:announcement:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6904, 69, '公告删除', NULL, 'system:announcement:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6905, 69, '公告发布', NULL, 'system:announcement:publish', NULL, NULL, 'BUTTON', 0, 1, 5, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6701, 67, '运维监控查询', NULL, 'system:monitor:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (9101, 91, '后端接口查询', NULL, 'devtools:api:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (7101, 71, '个人信息查询', NULL, 'profile:info:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (7201, 72, '修改密码', NULL, 'profile:password:edit', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-19 17:24:15.819191') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (11, 1, '仪表盘', '/home/dashboard', 'Dashboard', NULL, 'Odometer', 'MENU', 0, 1, 9, 'views/dashboard/Dashboard.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-23 11:28:53.080182') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (101, 10, 'Agents', '/ai/agents', 'AIAgentManage', NULL, 'Cpu', 'MENU', 1, 1, 3, 'views/ai/AgentManage.vue', '{"title":"Agents 管理"}', 1, '2026-08-19 19:22:50.370053', '2026-08-23 11:20:47.622202') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10, 0, '智能中心', '/ai', 'AI', '/ai/qa', 'MagicStick', 'DIRECTORY', 1, 1, 2, NULL, NULL, 1, '2026-08-19 19:22:50.370053', '2026-08-22 17:56:21.402419') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (102, 10, '知识库', '/ai/knowledges', 'AIDocumentManage', NULL, 'FolderOpened', 'MENU', 1, 1, 4, 'views/ai/DocumentManage.vue', '{"title":"知识库"}', 1, '2026-08-19 19:22:50.370053', '2026-08-23 11:20:47.622202') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (103, 10, 'Agent 会话', '/home/chat', 'AIChat', NULL, 'ChatLineRound', 'MENU', 1, 1, 0, 'views/ai/AIChat.vue', '{"title":"Agent 会话"}', 1, '2026-08-19 19:22:50.370053', '2026-08-23 21:07:05.677819') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (1, 0, '工作台', '/home', 'Home', '/home/briefings', 'HomeFilled', 'DIRECTORY', 1, 1, 1, NULL, NULL, 0, '2026-08-19 17:24:05.741707', '2026-08-23 22:50:11.416873') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (104, 10, 'MCP Hub', '/ai/mcp', 'AIMcpHub', NULL, 'Link', 'MENU', 1, 1, 5, 'views/ai/McpHub.vue', '{"title":"MCP Hub"}', 1, '2026-08-19 19:22:50.370053', '2026-08-23 11:20:47.622202') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (65, 6, '参数配置', '/system/config', 'SystemConfig', NULL, 'Tools', 'MENU', 1, 1, 1, 'views/system/SystemConfig.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-22 12:37:38.934612') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6501, 65, '参数配置查询', NULL, 'system:config:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-22 12:37:38.934612') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6502, 65, '参数配置修改', NULL, 'system:config:edit', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1, '2026-08-19 17:24:15.819191', '2026-08-22 12:37:38.934612') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (67, 6, '运维监控', '/monitor/ops', 'OpsMonitor', NULL, 'Odometer', 'MENU', 1, 1, 4, 'views/system/SystemMonitor.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-22 12:37:38.934612') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (91, 6, '后端接口', '/devtools/api', 'BackendApi', NULL, 'Document', 'MENU', 1, 1, 5, 'views/devtools/SwaggerEmbed.vue', '{"fullBleed":true,"hideTabBar":false,"hideBreadcrumb":true,"hideSecondaryAside":false}', 1, '2026-08-19 17:24:05.741707', '2026-08-22 12:37:38.934612') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (68, 6, '操作日志', '/system/operation-log', 'OperationLog', NULL, 'Document', 'MENU', 1, 1, 2, 'views/system/OperationLogList.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-22 12:37:38.934612') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (69, 6, '公告管理', '/system/announcement', 'AnnouncementManage', NULL, 'Notification', 'MENU', 1, 1, 3, 'views/system/AnnouncementList.vue', NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-22 12:37:38.934612') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (7, 0, '个人中心', '/profile', 'Profile', '/profile/info', 'UserFilled', 'DIRECTORY', 1, 1, 9, NULL, NULL, 1, '2026-08-19 17:24:05.741707', '2026-08-22 17:56:23.166272') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (109, 10, '知识检索', '/ai/qa', 'AIDocumentQA', NULL, 'Search', 'MENU', 1, 1, 1, 'views/ai/DocumentQA.vue', '{"title":"知识检索"}', 1, '2026-08-22 17:56:23.290371', '2026-08-22 17:56:23.290371') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10901, 109, '知识检索查询', NULL, 'ai:qa:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-08-22 17:56:29.986038', '2026-08-22 17:56:29.986038') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (106, 10, '知识图谱', '/ai/knowledge-graph', 'AIKnowledgeGraph', NULL, 'Connection', 'MENU', 1, 1, 2, 'views/ai/KnowledgeGraph.vue', '{"title":"知识图谱"}', 1, '2026-08-19 19:22:50.370053', '2026-08-23 11:20:47.622202') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (610, 6, '开放能力', '/system/open-api', 'OpenApiCapabilities', NULL, 'Connection', 'MENU', 1, 1, 7, 'views/system/OpenApiCapabilities.vue', '{"title":"开放能力"}', 1, '2026-08-23 14:15:38.828989', '2026-08-23 14:15:38.828989') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (11001, 110, '监控查询', NULL, 'ai:monitor:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-09-05 20:11:22.80197', '2026-09-05 20:11:22.80197') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (120201, 1202, '额度查询', NULL, 'gateway:quota:list', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-10-22 06:25:50.006667', '2026-10-22 09:36:52.313516') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (120202, 1202, '额度维护', NULL, 'gateway:quota:update', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1, '2026-10-22 06:25:50.006667', '2026-10-22 09:36:52.313516') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (120301, 1203, '用量查询', NULL, 'gateway:usage:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-10-22 06:25:50.006667', '2026-10-22 09:36:52.313516') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10501, 105, '供应商查询', NULL, 'gateway:provider:list', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-10-22 09:36:52.313516', '2026-10-22 09:36:52.313516') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10502, 105, '供应商维护', NULL, 'gateway:provider:update', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1, '2026-10-22 09:36:52.313516', '2026-10-22 09:36:52.313516') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10113, 101, '智能体修改', NULL, 'ai:agent:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1, '2026-10-23 06:22:12.851725', '2026-10-23 06:22:12.851725') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10212, 102, '文档编辑', NULL, 'ai:document:edit', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1, '2026-10-23 06:22:12.851725', '2026-10-23 06:22:12.851725') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10211, 102, '文档上传', NULL, 'ai:document:add', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-10-23 06:22:12.851725', '2026-10-23 06:22:12.851725') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10411, 104, 'MCP 配置维护', NULL, 'ai:mcp:edit', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-10-23 06:22:12.851725', '2026-10-23 06:22:12.851725') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (110, 10, '执行监控', '/ai/monitor', 'AIAgentMonitor', NULL, 'Monitor', 'MENU', 1, 1, 7, 'views/ai/AgentMonitor.vue', '{"title":"执行监控"}', 1, '2026-09-05 20:11:22.80197', '2026-10-22 07:55:35.139583') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (108, 10, '工作流编排', '/ai/agents/:id/graph', 'AIAgentGraphEditor', NULL, NULL, 'PAGE', 0, 1, 8, 'views/ai/AgentGraphEditor.vue', NULL, 1, '2026-08-19 19:22:50.370053', '2026-10-22 07:55:35.139583') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (5, 0, '账号管理', '/permission', 'Permission', '/permission/user', 'Key', 'DIRECTORY', 1, 1, 4, NULL, NULL, 1, '2026-08-19 17:24:05.741707', '2026-10-22 07:55:35.139583') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (6, 0, '系统设置', '/system', 'System', '/system/config', 'SetUp', 'DIRECTORY', 1, 1, 5, NULL, NULL, 1, '2026-08-19 17:24:05.741707', '2026-10-22 07:55:35.139583') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (12, 0, '模型网关', '/gateway', 'ModelGateway', '/gateway/providers', 'Connection', 'DIRECTORY', 1, 1, 3, NULL, NULL, 1, '2026-10-22 06:25:50.006667', '2026-10-22 09:36:37.037631') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (1201, 12, '模型设置', '/gateway/models', 'GatewayModels', NULL, 'Grid', 'MENU', 1, 1, 2, 'views/gateway/GatewayModelList.vue', '{"title":"模型设置"}', 1, '2026-10-22 06:25:50.006667', '2026-10-22 09:36:37.037631') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (1202, 12, '额度策略', '/gateway/quotas', 'GatewayQuotas', NULL, 'Histogram', 'MENU', 1, 1, 3, 'views/gateway/GatewayQuotaPolicy.vue', '{"title":"额度策略"}', 1, '2026-10-22 06:25:50.006667', '2026-10-22 09:36:37.037631') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (1203, 12, '调用用量', '/gateway/usage', 'GatewayUsage', NULL, 'DataLine', 'MENU', 1, 1, 4, 'views/gateway/GatewayUsageList.vue', '{"title":"调用用量"}', 1, '2026-10-22 06:25:50.006667', '2026-10-22 09:36:37.037631') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (1204, 12, '接入说明', '/gateway/guide', 'GatewayGuide', NULL, 'Document', 'MENU', 1, 1, 5, 'views/gateway/GatewayGuide.vue', '{"title":"接入说明"}', 1, '2026-10-22 06:25:50.006667', '2026-10-22 09:36:37.037631') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (105, 12, '供应商管理', '/gateway/providers', 'GatewayProviders', NULL, 'Connection', 'MENU', 1, 1, 1, 'views/gateway/GatewayProviderList.vue', '{"title":"供应商管理"}', 1, '2026-08-19 19:22:50.370053', '2026-10-22 09:36:40.606764') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (120101, 1201, '网关模型查询', NULL, 'gateway:model:list', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-10-22 06:25:50.006667', '2026-10-22 09:36:52.313516') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (120102, 1201, '网关模型维护', NULL, 'gateway:model:update', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1, '2026-10-22 06:25:50.006667', '2026-10-22 09:36:52.313516') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10214, 102, '分类维护', NULL, 'ai:category:edit', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1, '2026-10-23 06:22:12.851725', '2026-10-23 06:22:12.851725') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (61011, 610, '开放应用维护', NULL, 'system:openapi:edit', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-10-23 06:22:12.851725', '2026-10-23 06:22:12.851725') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10114, 101, '智能体删除', NULL, 'ai:agent:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1, '2026-10-23 06:22:12.851725', '2026-10-23 06:22:12.851725') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10213, 102, '文档删除', NULL, 'ai:document:remove', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1, '2026-10-23 06:22:12.851725', '2026-10-23 06:22:12.851725') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10115, 101, '工作流编辑', NULL, 'ai:agent:graph', NULL, NULL, 'BUTTON', 0, 1, 5, NULL, NULL, 1, '2026-10-23 06:22:12.851725', '2026-10-23 06:22:12.851725') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10111, 101, '智能体查询', NULL, 'ai:agent:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-10-23 06:22:12.851725', '2026-10-23 06:22:12.851725') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10112, 101, '智能体新增', NULL, 'ai:agent:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1, '2026-10-23 06:22:12.851725', '2026-10-23 06:22:12.851725') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status, create_time, update_time) VALUES (10601, 106, '知识图谱同步', NULL, 'ai:kg:sync', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1, '2026-10-23 06:28:54.78512', '2026-10-23 06:28:54.78512') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role (id, role_code, role_name, description, status, sort, create_time, update_time) VALUES (1, 'ADMIN', '超级管理员', '拥有全部菜单权限', 1, 1, '2026-08-19 17:23:56.606053', '2026-08-19 17:23:56.606053') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role (id, role_code, role_name, description, status, sort, create_time, update_time) VALUES (2, 'USER', '普通用户', '基础查看与个人中心权限', 1, 2, '2026-08-19 17:23:56.606053', '2026-08-19 17:23:56.606053') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10001, 1, 1) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10011, 1, 11) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10005, 1, 5) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10061, 1, 61) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10063, 1, 63) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10064, 1, 64) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10066, 1, 66) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10006, 1, 6) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10065, 1, 65) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10068, 1, 68) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10069, 1, 69) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10067, 1, 67) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10091, 1, 91) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10007, 1, 7) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10071, 1, 71) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10072, 1, 72) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (19001, 1, 9001) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (101101, 1, 1101) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106101, 1, 6101) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106102, 1, 6102) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106103, 1, 6103) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106104, 1, 6104) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106105, 1, 6105) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106301, 1, 6301) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106302, 1, 6302) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106303, 1, 6303) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106304, 1, 6304) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106401, 1, 6401) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106402, 1, 6402) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106403, 1, 6403) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106404, 1, 6404) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106405, 1, 6405) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106601, 1, 6601) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106602, 1, 6602) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106603, 1, 6603) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106604, 1, 6604) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106501, 1, 6501) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106502, 1, 6502) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106801, 1, 6801) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106901, 1, 6901) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106902, 1, 6902) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106903, 1, 6903) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106904, 1, 6904) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106905, 1, 6905) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (106701, 1, 6701) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (109101, 1, 9101) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (107101, 1, 7101) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (107201, 1, 7201) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10010, 1, 10) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10101, 1, 101) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10102, 1, 102) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10103, 1, 103) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10104, 1, 104) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10106, 1, 106) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10108, 1, 108) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10109, 1, 109) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (110901, 1, 10901) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10610, 1, 610) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567715945754626, 2, 9001) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567715945754627, 2, 10) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567715945754628, 2, 103) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474882, 2, 109) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474883, 2, 10901) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474884, 2, 106) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474885, 2, 101) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474886, 2, 102) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474887, 2, 104) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474889, 2, 108) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474890, 2, 7) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474891, 2, 71) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474892, 2, 7101) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474893, 2, 72) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474894, 2, 7201) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10110, 1, 110) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (111001, 1, 11001) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (130201, 1, 120201) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (130202, 1, 120202) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (130301, 1, 120301) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (20501, 1, 10501) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (20502, 1, 10502) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10012, 1, 12) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (11201, 1, 1201) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (11202, 1, 1202) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (11203, 1, 1203) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (11204, 1, 1204) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (10105, 1, 105) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (130101, 1, 120101) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (130102, 1, 120102) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474895, 1, 10111) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474896, 1, 10112) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474897, 1, 10113) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474898, 1, 10114) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474899, 1, 10115) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474900, 1, 10211) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474901, 1, 10212) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474902, 1, 10213) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474903, 1, 10214) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474904, 1, 10411) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474905, 1, 61011) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474906, 2, 10111) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474907, 2, 10112) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474908, 2, 10113) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474909, 2, 10114) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474910, 2, 10115) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474911, 2, 10211) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474912, 2, 10212) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474913, 2, 10213) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474914, 2, 10214) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474915, 2, 10411) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474916, 1, 10601) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_role_menu (id, role_id, menu_id) VALUES (2091567716004474917, 2, 10601) ON CONFLICT DO NOTHING;
INSERT INTO public.sys_user (id, username, real_name, email, phone, password, role, status, create_time, update_time) VALUES (2072989840212291586, 'user', '演示用户', NULL, NULL, '$2a$10$TVWen3.CL4.Wy9lDeZyrveD1tlcleqFQteU73GFYvssjAhZOqOQy2', 'USER', 1, '2026-08-19 19:22:50.682876', '2026-08-24 00:47:04.606218') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_user (id, username, real_name, email, phone, password, role, status, create_time, update_time) VALUES (1, 'admin', '系统管理员', NULL, NULL, '$2a$10$US1qluaSU0GGTPG9Vz3QxeewavpNXz5WjYpVLJVvrQ9iBd/NAFKSK', 'ADMIN', 1, '2026-08-19 17:23:52.01484', '2026-10-21 10:57:27.395365') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_user (id, username, real_name, email, phone, password, role, status, create_time, update_time) VALUES (2112819046924599297, 'Lets', '垒知者', NULL, NULL, '$2a$10$NCsuAPUuNPzDCubF8KrLaus7ctVBsBTANAOR0kV3C5sLIh14nwfkC', 'USER', 1, '2026-10-21 16:11:29.720225', '2026-10-21 16:11:29.720225') ON CONFLICT DO NOTHING;
INSERT INTO public.sys_user (id, username, real_name, email, phone, password, role, status, create_time, update_time) VALUES (9000000000000000001, 'gateway-service', '模型网关服务账号', NULL, NULL, 'disabled', 'SERVICE', 1, '2026-10-22 06:24:34.959529', '2026-10-22 06:24:34.959529') ON CONFLICT DO NOTHING;

