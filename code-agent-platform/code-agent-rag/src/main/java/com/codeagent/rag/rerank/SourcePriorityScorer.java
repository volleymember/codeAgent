package com.codeagent.rag.rerank;

import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.search.RagSearchResult;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SourcePriorityScorer implements EvidenceScorer {
    @Override
    public double score(RagSearchRequest request, RagSearchResult result) {
        String type = normalize(result.evidenceType());
        String source = normalize(result.sourceSystem());
        String filePath = normalize(result.filePath());
        if ("JAVA_CODE".equals(type) || filePath.endsWith(".JAVA")) {
            return 1.0;
        }
        if ("GITLAB_DIFF".equals(type) || type.contains("DIFF")) {
            return 0.9;
        }
        if ("JENKINS_CONSOLE_LOG".equals(type) || type.contains("LOG")) {
            return 0.78;
        }
        if ("JENKINS_TEST_REPORT".equals(type) || type.contains("TEST")) {
            return 0.68;
        }
        if ("SONAR_ISSUES".equals(type) || "SONARQUBE".equals(source)) {
            return 0.56;
        }
        if ("MARKDOWN".equals(type) || "DOCUMENT".equals(type) || "DOC".equals(source)) {
            return 0.36;
        }
        return 0.5;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}
