-- 知枢可集成框架：品牌文案更新（仅更新 sys_config，可单独执行）
-- 用法：psql -U postgres -d zhishu_integrable_framework -f zhishu-core/src/main/resources/db/update-branding.sql

UPDATE sys_config
SET
    system_name = '知枢可集成框架',
    english_title = 'ZhiShu Integrable Framework',
    copyright = '© 2026 知枢可集成框架 · MIT 开源',
    system_introduction = '一套面向企业数字化与智能化应用集成的模块化开发底座，通过统一技术架构、业务组件、伙伴 SSO 与行业扩展能力，帮助企业快速构建可集成、可扩展的应用系统。',
    update_time = CURRENT_TIMESTAMP
WHERE id = 1;
