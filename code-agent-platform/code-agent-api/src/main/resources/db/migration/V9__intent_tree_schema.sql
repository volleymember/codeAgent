CREATE TABLE intent_tree (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tree_code VARCHAR(128) NOT NULL,
    tree_name VARCHAR(255) NOT NULL,
    version INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    UNIQUE KEY uk_intent_tree_version (tree_code, version),
    INDEX idx_intent_tree_status (status),
    INDEX idx_intent_tree_code_status (tree_code, status)
);

CREATE TABLE intent_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tree_code VARCHAR(128) NOT NULL,
    version INT NOT NULL,
    node_code VARCHAR(128) NOT NULL,
    parent_code VARCHAR(128),
    node_name VARCHAR(255) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    description TEXT,
    keywords_json JSON,
    example_queries_json JSON,
    default_time_range_hours INT,
    allowed_tool_types_json JSON,
    required_evidence_types_json JSON,
    enabled TINYINT DEFAULT 1,
    sort_order INT DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    UNIQUE KEY uk_intent_node_code (tree_code, version, node_code),
    INDEX idx_intent_node_tree (tree_code, version),
    INDEX idx_intent_node_leaf (tree_code, version, node_type, enabled),
    INDEX idx_intent_node_parent (tree_code, version, parent_code)
);
