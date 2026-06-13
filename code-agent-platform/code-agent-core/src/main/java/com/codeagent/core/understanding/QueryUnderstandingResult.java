package com.codeagent.core.understanding;

import java.util.List;

public record QueryUnderstandingResult(
        String originalQuery,
        String normalizedQuery,
        List<String> keywords,
        List<String> symptoms,
        List<String> projectHints,
        List<String> serviceHints,
        String environment,
        String timeExpression,
        String errorMessage,
        String traceId,
        String commitSha,
        String branch,
        List<String> possibleExternalRefs,
        double uncertainty
) {
    public QueryUnderstandingResult {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        projectHints = projectHints == null ? List.of() : List.copyOf(projectHints);
        serviceHints = serviceHints == null ? List.of() : List.copyOf(serviceHints);
        possibleExternalRefs = possibleExternalRefs == null ? List.of() : List.copyOf(possibleExternalRefs);
        uncertainty = Math.max(0.0, Math.min(1.0, uncertainty));
    }
}
