CREATE TABLE memory_recall_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64),
    session_id VARCHAR(64),
    project_key VARCHAR(128) NOT NULL,
    task_type VARCHAR(64),
    agent_name VARCHAR(128),
    phase VARCHAR(64),
    query_text TEXT,
    core_rule_count INT DEFAULT 0,
    episode_count INT DEFAULT 0,
    recalled_episode_ids JSON,
    max_score DOUBLE,
    latency_ms BIGINT,
    created_at DATETIME NOT NULL,
    INDEX idx_memory_recall_project (project_key, id),
    INDEX idx_memory_recall_task (task_no),
    INDEX idx_memory_recall_session (session_id)
);
