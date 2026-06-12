CREATE TABLE agent_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    task_type VARCHAR(64) NOT NULL,
    project_key VARCHAR(128),
    user_input TEXT,
    status VARCHAR(32) NOT NULL,
    current_round INT DEFAULT 0,
    max_rounds INT DEFAULT 3,
    request_json JSON,
    final_report TEXT,
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_agent_task_project (project_key),
    INDEX idx_agent_task_status (status)
);

CREATE TABLE agent_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    task_no VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_agent_session_task (task_no)
);

CREATE TABLE agent_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL,
    step_id VARCHAR(64) NOT NULL,
    agent_name VARCHAR(128) NOT NULL,
    tool_name VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    input_json JSON,
    output_summary TEXT,
    error_message TEXT,
    started_at DATETIME,
    finished_at DATETIME,
    INDEX idx_agent_step_task (task_no)
);

CREATE TABLE tool_call_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    input_json JSON,
    output_summary TEXT,
    raw_output_uri VARCHAR(512),
    status VARCHAR(32),
    latency_ms BIGINT,
    error_message TEXT,
    created_at DATETIME,
    INDEX idx_tool_call_task (task_no),
    INDEX idx_tool_call_tool (tool_name)
);

CREATE TABLE evidence_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    evidence_no VARCHAR(64) NOT NULL UNIQUE,
    task_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(64),
    source_uri VARCHAR(512),
    raw_ref VARCHAR(512),
    title VARCHAR(255),
    summary TEXT,
    score DOUBLE,
    metadata JSON,
    created_at DATETIME,
    INDEX idx_evidence_task (task_no),
    INDEX idx_evidence_source_type (source_type)
);

CREATE TABLE document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_id VARCHAR(64) NOT NULL UNIQUE,
    project_key VARCHAR(128),
    title VARCHAR(255),
    doc_type VARCHAR(64),
    source_type VARCHAR(64),
    source_uri VARCHAR(512),
    raw_ref VARCHAR(512),
    status VARCHAR(32),
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_document_project (project_key)
);

CREATE TABLE document_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chunk_id VARCHAR(64) NOT NULL UNIQUE,
    doc_id VARCHAR(64) NOT NULL,
    project_key VARCHAR(128),
    module_name VARCHAR(128),
    doc_type VARCHAR(64),
    source_type VARCHAR(64),
    source_uri VARCHAR(512),
    title VARCHAR(255),
    content TEXT,
    metadata JSON,
    vector_id VARCHAR(128),
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_document_chunk_doc (doc_id),
    INDEX idx_document_chunk_project (project_key)
);

CREATE TABLE memory_core (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_key VARCHAR(128),
    type VARCHAR(64),
    content TEXT,
    priority INT DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_memory_core_project (project_key)
);

CREATE TABLE memory_episode (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    episode_id VARCHAR(64) NOT NULL UNIQUE,
    project_key VARCHAR(128),
    symptoms_json JSON,
    root_cause TEXT,
    fix_content TEXT,
    verified_by TEXT,
    source_uri VARCHAR(512),
    reliability VARCHAR(64),
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_memory_episode_project (project_key)
);

CREATE TABLE llm_call_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    model VARCHAR(128),
    task_type VARCHAR(64),
    input_tokens BIGINT,
    output_tokens BIGINT,
    latency_ms BIGINT,
    status VARCHAR(32),
    error_message TEXT,
    created_at DATETIME,
    INDEX idx_llm_call_task (task_no)
);

CREATE TABLE integration_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    platform VARCHAR(64) NOT NULL,
    project_key VARCHAR(128),
    base_url VARCHAR(512) NOT NULL,
    auth_type VARCHAR(64) NOT NULL,
    secret_ref VARCHAR(512) NOT NULL,
    enabled TINYINT DEFAULT 1,
    last_verified_at DATETIME,
    connection_status VARCHAR(64),
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_integration_platform (platform),
    INDEX idx_integration_project (project_key)
);
