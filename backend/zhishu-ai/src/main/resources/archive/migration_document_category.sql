-- 知识库分类（每个分类 = 一类知识库）+ knowledges.category_id

CREATE TABLE IF NOT EXISTS knowledges_category (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(500),
    sort_order   INT          NOT NULL DEFAULT 0,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_knowledges_category_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_knowledges_category_status ON knowledges_category (status, sort_order);

COMMENT ON TABLE knowledges_category IS '知识库分类：每个分类对应一类知识库';
COMMENT ON COLUMN knowledges_category.code IS '唯一编码';
COMMENT ON COLUMN knowledges_category.name IS '知识库名称';

-- 兼容旧表名 documents：优先写 knowledges
DO $$
BEGIN
    IF to_regclass('public.knowledges') IS NOT NULL THEN
        ALTER TABLE knowledges ADD COLUMN IF NOT EXISTS category_id BIGINT;
        CREATE INDEX IF NOT EXISTS idx_knowledges_category ON knowledges (category_id);
        EXECUTE 'COMMENT ON COLUMN knowledges.category_id IS ''所属知识库分类 ID，可空=未分类''';
    ELSIF to_regclass('public.documents') IS NOT NULL THEN
        ALTER TABLE documents ADD COLUMN IF NOT EXISTS category_id BIGINT;
        CREATE INDEX IF NOT EXISTS idx_documents_category ON documents (category_id);
        EXECUTE 'COMMENT ON COLUMN documents.category_id IS ''所属知识库分类 ID，可空=未分类''';
    END IF;
END $$;

INSERT INTO knowledges_category (code, name, description, sort_order, status)
VALUES
    ('general', '通用知识库', '默认知识库，未单独归类的文档可放入此处', 0, 'ENABLED'),
    ('standard', '技术规范', '标准、规程、规范类文档', 10, 'ENABLED'),
    ('ops', '运维手册', '运维操作、故障处理类文档', 20, 'ENABLED')
ON CONFLICT (code) DO NOTHING;

-- 存量未分类文档归入通用知识库
DO $$
BEGIN
    IF to_regclass('public.knowledges') IS NOT NULL THEN
        UPDATE knowledges d
        SET category_id = c.id
        FROM knowledges_category c
        WHERE d.category_id IS NULL
          AND c.code = 'general';
    ELSIF to_regclass('public.documents') IS NOT NULL THEN
        UPDATE documents d
        SET category_id = c.id
        FROM knowledges_category c
        WHERE d.category_id IS NULL
          AND c.code = 'general';
    END IF;
END $$;
