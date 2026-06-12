package com.codeagent.core.parallel;

import com.codeagent.core.agent.ToolPlan;
import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.rag.model.EvidenceType;
import com.codeagent.rag.model.RetrievalScope;
import com.codeagent.rag.search.RagSearchRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AgentWorkPlanner {
    private final AgentProperties properties;

    public AgentWorkPlanner(AgentProperties properties) {
        this.properties = properties;
    }

    public List<AgentWorkItem> plan(String taskNo,
                                    String sessionId,
                                    CreateAgentTaskCommand command,
                                    List<ToolPlan> toolPlans) {
        List<AgentWorkItem> workItems = new ArrayList<>();
        if (toolPlans != null) {
            for (ToolPlan plan : toolPlans) {
                workItems.add(fromToolPlan(taskNo, sessionId, plan));
            }
        }
        if (properties.isEnableParallelCodeSearch()) {
            workItems.add(codeSearch(taskNo, sessionId, command, workItems.size() + 1));
        }
        if (properties.isEnableParallelDocumentRetrieval()
                && (hasText(command.confluencePageUrl()) || hasText(command.openApiUrl()))) {
            workItems.add(documentRetrieval(taskNo, sessionId, command, workItems.size() + 1));
        }
        return workItems.stream()
                .limit(Math.max(1, properties.getMaxToolCallsPerTask()))
                .toList();
    }

    private AgentWorkItem fromToolPlan(String taskNo, String sessionId, ToolPlan plan) {
        AgentWorkType workType = classify(plan);
        return new AgentWorkItem(
                taskNo,
                sessionId,
                plan.stepId(),
                plan.agentName(),
                AgentWorkSource.MCP_TOOL,
                workType,
                plan.toolName(),
                plan.input(),
                null,
                plan.required(),
                properties.getMaxAgentRetries() + 1,
                properties.getDefaultSubtaskTimeoutMs(),
                plan.routeScore(),
                plan.routeReason(),
                plan.estimatedOutputTokens()
        );
    }

    private AgentWorkItem codeSearch(String taskNo, String sessionId, CreateAgentTaskCommand command, int index) {
        RagSearchRequest request = new RagSearchRequest(
                command.projectKey(),
                null,
                null,
                null,
                null,
                List.of(EvidenceType.JAVA_CODE, EvidenceType.GITLAB_DIFF),
                null,
                null,
                List.of(RetrievalScope.VECTOR, RetrievalScope.KEYWORD),
                buildQuery(command),
                properties.getParallelCodeSearchTopK(),
                properties.getParallelCodeSearchTopK(),
                properties.getParallelCodeSearchTopK()
        );
        return new AgentWorkItem(
                taskNo,
                sessionId,
                "P" + index,
                "CodeSearchAgent",
                AgentWorkSource.RAG_SEARCH,
                AgentWorkType.CODE_SEARCH,
                "rag.code_search",
                Map.of(),
                request,
                false,
                properties.getMaxAgentRetries() + 1,
                properties.getDefaultSubtaskTimeoutMs(),
                0.72,
                "RAG code search expands evidence beyond explicit external tool inputs.",
                properties.getParallelCodeSearchTopK() * 260
        );
    }

    private AgentWorkItem documentRetrieval(String taskNo, String sessionId, CreateAgentTaskCommand command, int index) {
        RagSearchRequest request = new RagSearchRequest(
                command.projectKey(),
                null,
                null,
                null,
                null,
                List.of(EvidenceType.MARKDOWN, EvidenceType.DOCUMENT),
                null,
                null,
                List.of(RetrievalScope.VECTOR, RetrievalScope.KEYWORD),
                buildQuery(command),
                properties.getParallelDocumentTopK(),
                properties.getParallelDocumentTopK(),
                properties.getParallelDocumentTopK()
        );
        return new AgentWorkItem(
                taskNo,
                sessionId,
                "P" + index,
                "DocumentRetrievalAgent",
                AgentWorkSource.RAG_SEARCH,
                AgentWorkType.DOCUMENT_RETRIEVAL,
                "rag.document_retrieval",
                Map.of("confluencePageUrl", value(command.confluencePageUrl()), "openApiUrl", value(command.openApiUrl())),
                request,
                false,
                properties.getMaxAgentRetries() + 1,
                properties.getDefaultSubtaskTimeoutMs(),
                0.68,
                "RAG document retrieval recalls indexed docs and API specifications for task context.",
                properties.getParallelDocumentTopK() * 240
        );
    }

    private AgentWorkType classify(ToolPlan plan) {
        String normalized = (plan.toolName() + " " + plan.agentName() + " " + plan.routeReason())
                .toLowerCase(Locale.ROOT);
        if (normalized.contains("test_report") || normalized.contains("test")) {
            return AgentWorkType.TEST_EXECUTION;
        }
        if (normalized.contains("console_log") || normalized.contains("failed_stage")
                || normalized.contains("build_status") || normalized.contains("jenkins")) {
            return AgentWorkType.CI_LOG_ANALYSIS;
        }
        if (normalized.contains("commit") || normalized.contains("review")) {
            return AgentWorkType.GIT_HISTORY_ANALYSIS;
        }
        if (normalized.contains("diff") || normalized.contains("code")) {
            return AgentWorkType.CODE_SEARCH;
        }
        if (normalized.contains("sonar") || normalized.contains("quality")
                || normalized.contains("coverage") || normalized.contains("complexity")) {
            return AgentWorkType.QUALITY_SCAN;
        }
        if (normalized.contains("doc") || normalized.contains("confluence") || normalized.contains("openapi")) {
            return AgentWorkType.DOCUMENT_RETRIEVAL;
        }
        return AgentWorkType.GENERIC_TOOL;
    }

    private String buildQuery(CreateAgentTaskCommand command) {
        return "%s project=%s gitlab=%s jenkins=%s sonar=%s jira=%s confluence=%s openapi=%s".formatted(
                value(command.taskType()),
                value(command.projectKey()),
                value(command.gitlabMrUrl()),
                value(command.jenkinsBuildUrl()),
                value(command.sonarqubeProjectKey()),
                value(command.jiraIssueKey()),
                value(command.confluencePageUrl()),
                value(command.openApiUrl())
        ).replaceAll("\\s+", " ").trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
