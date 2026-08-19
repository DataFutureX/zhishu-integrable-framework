-- 已有库补丁：登记万象 MCP 上游并绑定内置 Agent
-- psql -d zhishu_integrable_framework -f patch_wanxiang_mcp_upstream.sql

INSERT INTO ai_mcp_upstream (
    code, name, protocol, base_url, endpoint, auth_header_enc,
    request_timeout_ms, status, remark, created_by
) VALUES (
    'wanxiang-monitor',
    '万象监测 MCP',
    'STREAMABLE_HTTP',
    'http://127.0.0.1:8080',
    '/mcp',
    'Bearer dev-wanxiang-mcp-key',
    20000,
    'ENABLED',
    '知枢 Agent 查询监测/巡检/NL2SQL 的默认上游',
    'system'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO ai_agent_mcp_upstream (agent_id, upstream_id)
SELECT a.id, u.id
FROM ai_agent a
CROSS JOIN ai_mcp_upstream u
WHERE u.code = 'wanxiang-monitor'
  AND a.code IN ('monitor_default', 'inspection_agent', 'nl2sql_agent')
ON CONFLICT (agent_id, upstream_id) DO NOTHING;

UPDATE ai_agent
SET capabilities = '["RAG","MCP_TOOLS"]',
    update_time = CURRENT_TIMESTAMP
WHERE code = 'monitor_default' AND is_builtin = TRUE;

UPDATE ai_agent
SET capabilities = '["MCP_TOOLS"]',
    update_time = CURRENT_TIMESTAMP
WHERE code IN ('inspection_agent', 'nl2sql_agent') AND is_builtin = TRUE;
