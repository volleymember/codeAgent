package com.codeagent.core.understanding;

import com.codeagent.common.enums.ModelTaskType;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.llm.client.LlmClient;
import com.codeagent.llm.model.LlmRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QueryUnderstandingService {
    private final LlmClient llmClient;

    public QueryUnderstandingService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public QueryUnderstandingResult understand(String taskNo, String sessionId, CreateAgentTaskCommand command) {
        String systemPrompt = """
                You are CodeAgent QueryUnderstandingService.
                Parse the user's engineering investigation request into strict JSON only.
                Do not include markdown or explanatory text.
                Preserve uncertainty when fields are absent.
                Output exactly these fields:
                {
                  "originalQuery": "",
                  "normalizedQuery": "",
                  "keywords": [],
                  "symptoms": [],
                  "projectHints": [],
                  "serviceHints": [],
                  "environment": "",
                  "timeExpression": "",
                  "errorMessage": "",
                  "traceId": "",
                  "commitSha": "",
                  "branch": "",
                  "possibleExternalRefs": [],
                  "uncertainty": 0.0
                }
                """;
        String original = originalQuery(command);
        String userPrompt = JsonSupport.toJson(Map.of(
                "originalQuery", original,
                "taskCommand", command
        ));
        JsonNode node = LlmJsonSupport.parseObject(llmClient.chat(new LlmRequest(taskNo, sessionId,
                ModelTaskType.QUERY_UNDERSTANDING, systemPrompt, userPrompt, 1800, 0.1)).content());
        return new QueryUnderstandingResult(
                text(node, "originalQuery", original),
                text(node, "normalizedQuery", original),
                list(node, "keywords"),
                list(node, "symptoms"),
                list(node, "projectHints"),
                list(node, "serviceHints"),
                text(node, "environment", ""),
                text(node, "timeExpression", ""),
                text(node, "errorMessage", ""),
                text(node, "traceId", ""),
                text(node, "commitSha", command.commitSha()),
                text(node, "branch", command.branch()),
                list(node, "possibleExternalRefs"),
                node.path("uncertainty").asDouble(0.5)
        );
    }

    private String originalQuery(CreateAgentTaskCommand command) {
        if (hasText(command.query())) {
            return command.query();
        }
        return "%s project=%s service=%s gitlab=%s jenkins=%s sonar=%s jira=%s".formatted(
                command.taskType(), command.projectKey(), value(command.serviceName()),
                value(command.gitlabMrUrl()), value(command.jenkinsBuildUrl()),
                value(command.sonarqubeProjectKey()), value(command.jiraIssueKey()))
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<String> list(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            return List.of();
        }
        return JsonSupport.mapper().convertValue(value,
                JsonSupport.mapper().getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText(null);
        return hasText(value) ? value : value(fallback);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
