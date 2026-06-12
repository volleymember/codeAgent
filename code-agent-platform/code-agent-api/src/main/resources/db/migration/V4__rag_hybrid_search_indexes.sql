ALTER TABLE document_chunk
    ADD COLUMN keywords TEXT NULL AFTER title,
    ADD FULLTEXT INDEX ft_document_chunk_hybrid (symbol_name, title, keywords, content),
    ADD INDEX idx_document_chunk_created (created_at),
    ADD INDEX idx_document_chunk_symbol (symbol_name);
