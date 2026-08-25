-- documents → knowledges（表名重命名，幂等）

DO $$
BEGIN
    IF to_regclass('public.documents') IS NOT NULL
       AND to_regclass('public.knowledges') IS NULL THEN
        ALTER TABLE documents RENAME TO knowledges;
    END IF;
END $$;

ALTER INDEX IF EXISTS idx_documents_file_name RENAME TO idx_knowledges_file_name;
ALTER INDEX IF EXISTS idx_documents_processed RENAME TO idx_knowledges_processed;
ALTER INDEX IF EXISTS idx_documents_upload_time RENAME TO idx_knowledges_upload_time;
ALTER INDEX IF EXISTS idx_documents_category RENAME TO idx_knowledges_category;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'documents_id_seq')
       AND NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = 'knowledges_id_seq') THEN
        ALTER SEQUENCE documents_id_seq RENAME TO knowledges_id_seq;
    END IF;
END $$;

COMMENT ON TABLE knowledges IS '知识文档元数据：上传文件信息与解析文本';
COMMENT ON COLUMN knowledges.category_id IS '所属知识库分类 ID';
