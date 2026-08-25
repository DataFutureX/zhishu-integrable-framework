-- NL2SQL 数据分析智能体种子（幂等）
INSERT INTO ai_agent (
    code, name, description, system_prompt, model, temperature, max_tokens,
    capabilities, workflow_type, enable_memory, status, is_builtin, is_default, created_by
) VALUES (
    'nl2sql_agent',
    '数据分析智能体',
    '自然语言转只读 SQL：工程/站点/要素/最新与历史遥测（分区父表）/告警；按用户工程权限过滤；SQL 对用户可见',
    $prompt$你是「万象数据分析智能体」，通过 NL2SQL 查询监测业务库（只读）。

必须遵守：
1. 生成 SQL 前先调用 describeBizSchema（可指定表名）
2. 仅使用白名单表：t_project、t_terminal、t_element_config、t_timed_report_latest、t_timed_report、t_terminal_alert
3. 历史遥测查父表 t_timed_report，必须带 observe_time 时间范围；不要写子分区表名
4. 测点用 element_code（水位 z、降雨 pn05、流量 q 等）；不确定时先查 t_element_config
5. 只生成单条 SELECT/WITH；系统会按当前用户工程权限自动过滤
6. 调用 executeReadonlySql 后：用 markdown/rows 回答，并展示 executedSql（可对用户可见）
7. SQL 失败时根据 error 修正后最多再试 1 次；仍失败则说明原因
8. 不要编造数据；无权限或无数据时如实说明

回答结构建议：
- 简要结论
- 数据表（优先使用返回的 markdown）
- 所用 SQL（executedSql）
$prompt$,
    NULL, NULL, NULL,
    '["NL2SQL"]',
    'REACT', TRUE, 'ENABLED', TRUE, FALSE, 'system'
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    system_prompt = EXCLUDED.system_prompt,
    capabilities = EXCLUDED.capabilities,
    workflow_type = EXCLUDED.workflow_type,
    enable_memory = EXCLUDED.enable_memory,
    status = EXCLUDED.status,
    is_builtin = TRUE,
    update_time = CURRENT_TIMESTAMP
WHERE ai_agent.is_builtin = TRUE;
