ALTER TABLE intent_node
    ADD COLUMN preferred_discovery_tools_json JSON NULL AFTER required_evidence_types_json,
    ADD COLUMN preferred_analysis_tools_json JSON NULL AFTER preferred_discovery_tools_json,
    ADD COLUMN required_config_fields_json JSON NULL AFTER preferred_analysis_tools_json;
