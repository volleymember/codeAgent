ALTER TABLE evidence_record
    ADD COLUMN project_key VARCHAR(128) NULL AFTER task_no,
    ADD COLUMN branch VARCHAR(128) NULL AFTER project_key,
    ADD COLUMN commit_id VARCHAR(128) NULL AFTER branch,
    ADD COLUMN build_id VARCHAR(128) NULL AFTER commit_id,
    ADD COLUMN evidence_type VARCHAR(64) NULL AFTER build_id,
    ADD COLUMN source_system VARCHAR(64) NULL AFTER evidence_type,
    ADD COLUMN source_url VARCHAR(1024) NULL AFTER source_uri,
    ADD COLUMN file_path VARCHAR(1024) NULL AFTER source_url,
    ADD COLUMN symbol_name VARCHAR(255) NULL AFTER file_path,
    ADD COLUMN keywords JSON NULL AFTER metadata,
    ADD INDEX idx_evidence_project (project_key),
    ADD INDEX idx_evidence_type (evidence_type);

ALTER TABLE document_chunk
    ADD COLUMN branch VARCHAR(128) NULL AFTER project_key,
    ADD COLUMN commit_id VARCHAR(128) NULL AFTER branch,
    ADD COLUMN build_id VARCHAR(128) NULL AFTER commit_id,
    ADD COLUMN evidence_no VARCHAR(64) NULL AFTER module_name,
    ADD COLUMN evidence_type VARCHAR(64) NULL AFTER evidence_no,
    ADD COLUMN source_system VARCHAR(64) NULL AFTER evidence_type,
    ADD COLUMN source_url VARCHAR(1024) NULL AFTER source_uri,
    ADD COLUMN file_path VARCHAR(1024) NULL AFTER source_url,
    ADD COLUMN symbol_name VARCHAR(255) NULL AFTER file_path,
    ADD COLUMN line_start INT NULL AFTER symbol_name,
    ADD COLUMN line_end INT NULL AFTER line_start,
    ADD COLUMN line_range VARCHAR(64) NULL AFTER line_end,
    ADD COLUMN chunk_index INT NULL AFTER line_range,
    ADD INDEX idx_document_chunk_filter (project_key, branch, commit_id, build_id, evidence_type),
    ADD INDEX idx_document_chunk_vector (vector_id),
    ADD INDEX idx_document_chunk_evidence (evidence_no);

CREATE TABLE retrieval_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    query_text TEXT NOT NULL,
    project_key VARCHAR(128) NOT NULL,
    branch VARCHAR(128),
    commit_id VARCHAR(128),
    build_id VARCHAR(128),
    evidence_types JSON,
    result_count INT NOT NULL,
    latency_ms BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_retrieval_log_project (project_key),
    INDEX idx_retrieval_log_created (created_at)
);
