-- 云起应用平台：品牌文案更新（仅更新 sys_config，可单独执行）
-- 用法：mysql -u root -p yunqi_admin < yqap-core/src/main/resources/db/update-branding.sql

UPDATE `sys_config`
SET
    `system_name` = '云起应用平台',
    `english_title` = 'YunQi Application Platform',
    `copyright` = '© 2026 云起应用平台 · MIT 开源',
    `system_introduction` = '一套面向企业数字化应用建设的模块化开发基础平台，通过统一技术架构、业务组件、AI能力和行业扩展能力，帮助企业快速构建智能化应用系统。',
    `update_time` = NOW()
WHERE `id` = 1;
