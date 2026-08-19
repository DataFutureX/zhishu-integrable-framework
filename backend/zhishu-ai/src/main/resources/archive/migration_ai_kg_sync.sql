-- 知识图谱同步水位表（幂等）
CREATE TABLE IF NOT EXISTS ai_kg_sync_watermark (
    source_table       VARCHAR(64) PRIMARY KEY,
    last_sync_at       TIMESTAMP,
    max_source_time    TIMESTAMP,
    last_status        VARCHAR(32),
    last_message       VARCHAR(500),
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_kg_sync_watermark IS '知识图谱 PG→Neo4j 同步水位';
COMMENT ON COLUMN ai_kg_sync_watermark.source_table IS '源表名，如 t_project';
COMMENT ON COLUMN ai_kg_sync_watermark.max_source_time IS '已同步到的源表最大时间戳';
COMMENT ON COLUMN ai_kg_sync_watermark.last_status IS 'SUCCESS | FAILED | SKIPPED';
