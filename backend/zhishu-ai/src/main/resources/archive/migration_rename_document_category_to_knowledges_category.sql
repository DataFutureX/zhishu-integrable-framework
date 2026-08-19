-- document_category → knowledges_category（表名重命名，幂等）

DO $$
BEGIN
    IF to_regclass('public.document_category') IS NOT NULL
       AND to_regclass('public.knowledges_category') IS NULL THEN
        ALTER TABLE document_category RENAME TO knowledges_category;
    END IF;
END $$;

ALTER INDEX IF EXISTS document_category_pkey RENAME TO knowledges_category_pkey;
ALTER INDEX IF EXISTS uk_document_category_code RENAME TO uk_knowledges_category_code;
ALTER INDEX IF EXISTS idx_document_category_status RENAME TO idx_knowledges_category_status;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'document_category_id_seq')
       AND NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = 'knowledges_category_id_seq') THEN
        ALTER SEQUENCE document_category_id_seq RENAME TO knowledges_category_id_seq;
    END IF;
END $$;

COMMENT ON TABLE knowledges_category IS '知识库分类：每个分类对应一类知识库';
COMMENT ON COLUMN knowledges_category.code IS '唯一编码';
COMMENT ON COLUMN knowledges_category.name IS '知识库名称';
