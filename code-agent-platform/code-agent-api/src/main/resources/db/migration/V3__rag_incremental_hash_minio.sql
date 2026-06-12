ALTER TABLE evidence_record
    ADD COLUMN content_hash CHAR(64) NULL AFTER symbol_name,
    ADD COLUMN object_id VARCHAR(1024) NULL AFTER content_hash,
    ADD COLUMN file_size BIGINT NULL AFTER object_id,
    ADD INDEX idx_evidence_content_hash (content_hash),
    ADD INDEX idx_evidence_object_id (object_id),
    ADD INDEX idx_evidence_logical_source (
        project_key,
        branch,
        commit_id,
        build_id,
        evidence_type,
        source_system,
        source_url(255),
        file_path(255)
    );

ALTER TABLE document_chunk
    ADD COLUMN content_hash CHAR(64) NULL AFTER chunk_index,
    ADD INDEX idx_document_chunk_hash (content_hash);
