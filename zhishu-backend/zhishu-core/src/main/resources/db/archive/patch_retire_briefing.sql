-- =============================================================================
-- 简报（briefing）功能退役：清理遗留表
--
-- 背景：简报已跳出平台，由外部自行调度并调用 /open/v1/chat。
--       Java 侧已无任何类绑定 zhishu.briefing.*，也没有 /api/v1/internal/briefings/notify 端点，
--       仅剩两张历史表。application.yml 中的死配置块已同步移除。
--
-- 执行方式：仅手工执行（不纳入启动期自动脚本），因为表内有历史投递数据。
--   psql -h <host> -U <user> -d <db> -f db/patch_retire_briefing.sql
--
-- 开发库实测数据量（2026-10-23）：ai_briefing_delivery 52 行、ai_briefing_schedule 1 行。
-- =============================================================================

-- 0. 先看一眼要处理什么
SELECT 'ai_briefing_delivery' AS table_name, count(*) AS row_count FROM ai_briefing_delivery
UNION ALL
SELECT 'ai_briefing_schedule', count(*) FROM ai_briefing_schedule;

-- 1. 保守做法：只改名保留数据（应用侧已无引用，改名不会报错）
ALTER TABLE IF EXISTS ai_briefing_delivery RENAME TO ai_briefing_delivery_retired;
ALTER TABLE IF EXISTS ai_briefing_schedule RENAME TO ai_briefing_schedule_retired;

-- 顺带清理可能残留的序列名（不影响数据）
-- SELECT relname FROM pg_class WHERE relkind='S' AND relname LIKE 'ai_briefing%';

-- 2. 确认无需留档后再彻底删除（默认注释，按需放开）
-- DROP TABLE IF EXISTS ai_briefing_delivery_retired;
-- DROP TABLE IF EXISTS ai_briefing_schedule_retired;

-- 3. 验证：应返回 0 行
SELECT table_name FROM information_schema.tables
 WHERE table_schema = 'public'
   AND table_name LIKE 'ai_briefing%'
 ORDER BY table_name;
