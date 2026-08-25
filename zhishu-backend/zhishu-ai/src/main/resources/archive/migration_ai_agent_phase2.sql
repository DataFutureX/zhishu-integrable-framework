-- Agent 二期：document_ids + ai_agent_run（幂等）

ALTER TABLE ai_agent ADD COLUMN IF NOT EXISTS document_ids TEXT;
COMMENT ON COLUMN ai_agent.document_ids IS '绑定知识库文档 ID JSON 数组，空=全部';
COMMENT ON COLUMN ai_agent.workflow_type IS 'REACT | SEQUENTIAL | ROUTING | GRAPH';
COMMENT ON COLUMN ai_agent.workflow_config IS 'Graph JSON v1 或模板扩展';

CREATE TABLE IF NOT EXISTS ai_agent_run (
    id               BIGSERIAL PRIMARY KEY,
    agent_id         BIGINT       NOT NULL,
    conversation_id  VARCHAR(64),
    status           VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    current_node     VARCHAR(64),
    state_json       TEXT,
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_run_agent ON ai_agent_run (agent_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_agent_run_conv ON ai_agent_run (conversation_id);

COMMENT ON TABLE ai_agent_run IS '智能体 Graph/试运行执行记录（Checkpoint）';
