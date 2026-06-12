ALTER TABLE tool_call_record
    ADD COLUMN raw_tokens BIGINT NULL AFTER latency_ms,
    ADD COLUMN context_tokens BIGINT NULL AFTER raw_tokens,
    ADD COLUMN compression_ratio DOUBLE NULL AFTER context_tokens,
    ADD COLUMN sandboxed TINYINT DEFAULT 0 AFTER compression_ratio,
    ADD INDEX idx_tool_call_context_tokens (context_tokens);

ALTER TABLE llm_call_record
    ADD COLUMN estimated_input_tokens BIGINT NULL AFTER output_tokens,
    ADD COLUMN max_input_tokens BIGINT NULL AFTER estimated_input_tokens,
    ADD COLUMN budget_policy VARCHAR(64) NULL AFTER max_input_tokens,
    ADD INDEX idx_llm_call_budget (task_no, budget_policy);
