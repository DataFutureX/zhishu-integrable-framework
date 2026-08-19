# 归档：历史增量 SQL

本目录存放已合并进上级 `init_postgresql.sql` 的历史脚本，仅供旧库补丁与启动时 SchemaInitializer 幂等补齐使用。

新环境请勿逐个执行本目录脚本，直接使用 `../init_postgresql.sql`。

| 脚本 | 用途 |
|------|------|
| `migration_rename_documents_to_knowledges.sql` | `documents` → `knowledges` 表重命名 |
| `migration_rename_document_category_to_knowledges_category.sql` | `document_category` → `knowledges_category` |
| `migration_document_category.sql` | 知识库分类表 + `category_id`（现表名 `knowledges_category`） |
| `migration_chat_session.sql` | 会话元数据表 + 历史回填 |
| `migration_ai_agent.sql` | `ai_agent` + 默认智能体种子 |
| `migration_ai_agent_phase2.sql` | `document_ids` + `ai_agent_run` |
| `migration_ai_agent_report_period.sql` | 报表周期提示词修补 |
| `migration_ai_agent_graph_json_fix.sql` | Graph JSON 换行污染修复 |
| `migration_ai_agent_inspection.sql` | 巡检智能体种子 |
| `migration_ai_agent_i2_tools.sql` | Agent I2 工具相关补丁 |
| `migration_ai_agent_nl2sql.sql` | NL2SQL 相关补丁 |
| `migration_ai_agent_kg.sql` | 知识图谱智能体种子（仅 `KNOWLEDGE_GRAPH` → `kg_agent`） |
| `migration_ai_kg_sync.sql` | `ai_kg_sync_watermark`（PG→Neo4j 同步水位） |
| `migration_ai_briefing.sql` | `ai_briefing_schedule` / `ai_briefing_delivery` + 每日简报种子 |
