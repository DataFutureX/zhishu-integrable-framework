# 数据库脚本（PostgreSQL + PGVector）

## 新库初始化（只需这一份）

```bash
psql -U postgres -d wanxiang_monitor -f init_postgresql.sql
```

也可在模块根目录执行转发入口：

```bash
psql -U postgres -d wanxiang_monitor -f ../../database-init.sql
```

`init_postgresql.sql` 已合并本目录历史上全部 `migration_*` 的最终表结构，并包含：

- 知识库分类 / 文档 / 问答历史 / 会话
- 智能体与运行快照、模型配置（含种子 `monitor_default` / `inspection_agent` / `kg_agent`）
- **AI 简报**调度与投递表（`ai_briefing_schedule` / `ai_briefing_delivery`）及「每日监测简报」种子
- **知识图谱同步水位**表 `ai_kg_sync_watermark`（PG→Neo4j；图数据在 Neo4j，不在本脚本建图库）

- 需 PostgreSQL 14+，脚本内会 `CREATE EXTENSION IF NOT EXISTS vector`
- `vector_store` / `SPRING_AI_CHAT_MEMORY` 由 Spring AI 启动时自动建表
- 业务遥测表与菜单种子见 `backend/wanxiang-core/.../db/init_postgresql.sql`
- Neo4j 需单独部署；见模块根 [README](../../../../README.md) 的「知识图谱」与环境变量 `NEO4J_*` / `WANXIANG_KG_*`

## 旧库升级

增量脚本已归档到 [`archive/`](./archive/)。仅当现网库尚未执行过某次变更时，再按需挑选执行。

应用启动时 SchemaInitializer 仍会幂等执行 archive 中的关键脚本（表结构 / 种子 / 旧表重命名），包括：

- `BriefingSchemaInitializer` → `migration_ai_briefing.sql`
- `KgSchemaInitializer` → `migration_ai_kg_sync.sql`
- `AgentSchemaInitializer` → `migration_ai_agent*.sql`（含 `migration_ai_agent_kg.sql`）

## 目录结构

```
db/
├── init_postgresql.sql   # 新库唯一入口（最终态）
├── README.md
└── archive/              # 历史增量（旧库补丁 / 启动幂等）
```
