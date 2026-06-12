ALTER TABLE memory_episode
    ADD COLUMN helpful_count INT DEFAULT 0 AFTER hit_count,
    ADD COLUMN unhelpful_count INT DEFAULT 0 AFTER helpful_count,
    ADD COLUMN status VARCHAR(32) DEFAULT 'ACTIVE' AFTER unhelpful_count,
    ADD COLUMN feedback_json JSON NULL AFTER status,
    ADD INDEX idx_memory_episode_status (project_key, status, reliability);
