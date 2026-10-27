-- =============================================================================
-- MCP 调用审计方向归一（破坏性数据变更，需人工执行）
--
-- 背景：MCP 合并进 zhishu-mcp 双平面后，ai_mcp_call_log.direction 的旧取值语义反直觉：
--       OUT = 外部 Client 调用本平台（对外平面被调用），IN = 本平台调用上游。
--       新代码改写入 SERVE（对外提供）/ CONSUME（接入他方），读侧已兼容旧值，
--       因此「先部署新版本、再执行本脚本」是安全顺序；反过来做也只是过滤条件短暂落空。
--
-- 语义：仅做值映射，不删行、不改结构；重复执行为幂等（旧值已被改写，命中不到）。
--
-- 执行：psql -U <user> -d <db> -f patch_mcp_call_log_direction.sql
--       或用客户端整段执行；本脚本不会被启动期自动加载。
-- =============================================================================

UPDATE public.ai_mcp_call_log
SET direction = 'SERVE'
WHERE direction = 'OUT';

UPDATE public.ai_mcp_call_log
SET direction = 'CONSUME'
WHERE direction = 'IN';

-- 归一后核对分布
SELECT direction, COUNT(*) AS rows
FROM public.ai_mcp_call_log
GROUP BY direction
ORDER BY direction;
