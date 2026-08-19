-- 修复 workflow_config Graph JSON 中被误插入的真实换行（CTRL-CHAR 10）
-- 根因：SQL replace 使用 E'...\n...' 写入 JSON 文本列，破坏字符串字面量。

UPDATE ai_agent
SET workflow_config = replace(
        workflow_config,
        E'禁止臆造日期）\n- 相对时间',
        E'禁止臆造日期）\\n- 相对时间'
    ),
    update_time = CURRENT_TIMESTAMP
WHERE workflow_config IS NOT NULL
  AND workflow_config LIKE E'%禁止臆造日期）\n- 相对时间%';
