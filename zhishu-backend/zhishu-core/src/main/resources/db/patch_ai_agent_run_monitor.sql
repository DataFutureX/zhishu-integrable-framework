-- ---------------------------------------------------------------------------
-- Agent 执行监控：增强 ai_agent_run 表
-- ---------------------------------------------------------------------------

ALTER TABLE ai_agent_run
  ADD COLUMN IF NOT EXISTS user_message         TEXT,            -- 用户输入原文
  ADD COLUMN IF NOT EXISTS response_summary     TEXT,            -- 响应摘要（前 500 字）
  ADD COLUMN IF NOT EXISTS duration_ms          BIGINT,          -- 执行耗时（毫秒）
  ADD COLUMN IF NOT EXISTS error_message        VARCHAR(1000),   -- 失败原因
  ADD COLUMN IF NOT EXISTS model_name           VARCHAR(64),     -- 使用的模型名
  ADD COLUMN IF NOT EXISTS workflow_type        VARCHAR(32),     -- 工作流类型（SEQUENTIAL/GRAPH/REACT/ROUTING）
  ADD COLUMN IF NOT EXISTS user_id              VARCHAR(64),     -- 触发人（与 qa_history.user_id 一致）
  ADD COLUMN IF NOT EXISTS run_type             VARCHAR(16) NOT NULL DEFAULT 'CHAT',  -- CHAT | TRIAL
  ADD COLUMN IF NOT EXISTS ttft_ms              BIGINT,          -- TTFT（Time To First Token），首 Token 到达时间（毫秒）
  ADD COLUMN IF NOT EXISTS tpot_ms              BIGINT,          -- TPOT（Time Per Output Token），每个输出 Token 平均耗时（毫秒）
  ADD COLUMN IF NOT EXISTS token_count           INT;             -- 响应 Token 总数（所有 LLM 调用累计）

-- 监控查询索引
CREATE INDEX IF NOT EXISTS idx_agent_run_status ON ai_agent_run (status);
CREATE INDEX IF NOT EXISTS idx_agent_run_time ON ai_agent_run (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_agent_run_user ON ai_agent_run (user_id);
