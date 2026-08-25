-- Agent 会话元数据（标题可编辑；conversation_id 与 qa_history / Memory 对齐）

CREATE TABLE IF NOT EXISTS chat_session (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    scene           VARCHAR(32)  NOT NULL DEFAULT 'CHAT',
    title           VARCHAR(128) NOT NULL DEFAULT '新会话',
    agent_id        BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chat_session_conversation UNIQUE (conversation_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_session_user_scene_time
    ON chat_session (user_id, scene, update_time DESC);

COMMENT ON TABLE chat_session IS 'AI Agent 会话（标题/智能体元数据）';
COMMENT ON COLUMN chat_session.conversation_id IS '多轮会话 ID（与 qa_history / Memory 一致）';
COMMENT ON COLUMN chat_session.title IS '会话标题（可编辑）';

-- 从已有问答历史回填会话（取每个 conversation 最早一条问题作默认标题）
INSERT INTO chat_session (conversation_id, user_id, scene, title, agent_id, create_time, update_time)
SELECT DISTINCT ON (q.conversation_id)
    q.conversation_id,
    q.user_id,
    COALESCE(NULLIF(TRIM(q.scene), ''), 'CHAT'),
    LEFT(NULLIF(TRIM(q.question), ''), 40),
    q.agent_id,
    q.create_time,
    q.create_time
FROM qa_history q
WHERE q.conversation_id IS NOT NULL
  AND TRIM(q.conversation_id) <> ''
  AND q.user_id IS NOT NULL
  AND TRIM(q.user_id) <> ''
ORDER BY q.conversation_id, q.create_time ASC
ON CONFLICT (conversation_id) DO NOTHING;

UPDATE chat_session
SET title = '新会话'
WHERE title IS NULL OR TRIM(title) = '';
