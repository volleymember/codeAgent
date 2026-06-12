ALTER TABLE memory_core
    ADD COLUMN tags_json JSON NULL AFTER content,
    ADD COLUMN status VARCHAR(32) DEFAULT 'ACTIVE' AFTER tags_json,
    ADD COLUMN source_uri VARCHAR(512) NULL AFTER status,
    ADD INDEX idx_memory_core_scope_status (project_key, status, priority);

ALTER TABLE memory_episode
    ADD COLUMN symptom_signature VARCHAR(512) NULL AFTER symptoms_json,
    ADD COLUMN tags_json JSON NULL AFTER reliability,
    ADD COLUMN confidence_score DOUBLE NULL AFTER tags_json,
    ADD COLUMN hit_count INT DEFAULT 0 AFTER confidence_score,
    ADD COLUMN last_recalled_at DATETIME NULL AFTER hit_count,
    ADD INDEX idx_memory_episode_signature (project_key, symptom_signature),
    ADD INDEX idx_memory_episode_recall (project_key, reliability, updated_at);
