-- 报表默认周期规则：未指定日期时 日报=当日 / 月报=当月 / 年报=当年
-- （运行时另由 AgentTimeContext 注入服务器当前时间）
-- 注意：改写 workflow_config（JSON 文本）时，换行必须写成 \\n，不可用 E'...\n...' 插入真实换行，否则会破坏 JSON。

UPDATE ai_agent
SET system_prompt = replace(
        system_prompt,
        '- 相对时间（上周、过去一个月）请换算为具体时间范围',
        E'- 用户未指定日期时：日报=当日、月报=当月、年报=当年（以系统注入的当前时间为准，禁止臆造日期）\n- 相对时间（今日、昨天、上周、过去一个月）请按系统注入时间换算为具体时间范围'
    ),
    update_time = CURRENT_TIMESTAMP
WHERE system_prompt LIKE '%相对时间（上周、过去一个月）请换算为具体时间范围%';

UPDATE ai_agent
SET workflow_config = replace(
        workflow_config,
        '不要编造数据，不要调用工具。',
        '日报/月报/年报未指定日期时分别按当日/当月/当年理解（以系统注入时间为准），不要编造数据与日期，不要调用工具。'
    ),
    update_time = CURRENT_TIMESTAMP
WHERE workflow_config LIKE '%不要编造数据，不要调用工具。%';

UPDATE ai_agent
SET workflow_config = replace(
        workflow_config,
        '保留关键数据与表格，不要编造。',
        '保留关键数据、表格与正确的报告周期，不要编造日期或数据。'
    ),
    update_time = CURRENT_TIMESTAMP
WHERE workflow_config LIKE '%保留关键数据与表格，不要编造。%';

UPDATE ai_agent
SET workflow_config = replace(
        workflow_config,
        '- 相对时间（上周、过去一个月）请换算为具体时间范围',
        -- JSON 字符串内换行必须是两个字符 \ 与 n
        E'- 用户未指定日期时：日报=当日、月报=当月、年报=当年（以系统注入的当前时间为准，禁止臆造日期）\\n- 相对时间（今日、昨天、上周、过去一个月）请按系统注入时间换算为具体时间范围'
    ),
    update_time = CURRENT_TIMESTAMP
WHERE workflow_config LIKE '%相对时间（上周、过去一个月）请换算为具体时间范围%';

-- 修复历史上用真实换行污染 JSON 的 workflow_config
UPDATE ai_agent
SET workflow_config = replace(
        workflow_config,
        E'禁止臆造日期）\n- 相对时间',
        E'禁止臆造日期）\\n- 相对时间'
    ),
    update_time = CURRENT_TIMESTAMP
WHERE workflow_config LIKE E'%禁止臆造日期）\n- 相对时间%';
