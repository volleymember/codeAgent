package com.codeagent.storage.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface DocumentChunkSearchRepository {
    List<KeywordChunkSearchResult> searchByKeyword(String queryText,
                                                   String booleanQuery,
                                                   String likeQuery,
                                                   String projectKey,
                                                   String moduleName,
                                                   String branch,
                                                   String commitId,
                                                   String buildId,
                                                   List<String> evidenceTypes,
                                                   LocalDateTime createdFrom,
                                                   LocalDateTime createdTo,
                                                   int limit);
}
