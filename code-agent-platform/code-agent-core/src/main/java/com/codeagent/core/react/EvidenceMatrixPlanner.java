package com.codeagent.core.react;

import com.codeagent.core.intent.dto.IntentLeafView;
import com.codeagent.core.understanding.ProjectContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class EvidenceMatrixPlanner {
    public EvidenceMatrix plan(IntentLeafView intentLeaf, ProjectContext projectContext, Map<String, Object> knownFacts) {
        String intentCode = intentLeaf == null ? "" : value(intentLeaf.nodeCode()).toUpperCase(Locale.ROOT);
        List<String> evidenceTypes = new ArrayList<>(intentLeaf == null ? List.of() : intentLeaf.requiredEvidenceTypes());
        List<String> discoveryTools = new ArrayList<>(intentLeaf == null ? List.of() : intentLeaf.preferredDiscoveryTools());
        List<String> analysisTools = new ArrayList<>(intentLeaf == null ? List.of() : intentLeaf.preferredAnalysisTools());
        List<String> requiredConfig = new ArrayList<>(intentLeaf == null ? List.of() : intentLeaf.requiredConfigFields());

        if (discoveryTools.isEmpty()) {
            discoveryTools.addAll(defaultDiscovery(intentCode));
        }
        if (analysisTools.isEmpty()) {
            analysisTools.addAll(defaultAnalysis(intentCode));
        }
        if (requiredConfig.isEmpty()) {
            requiredConfig.addAll(defaultRequiredConfig(intentCode));
        }
        if (evidenceTypes.isEmpty()) {
            evidenceTypes.addAll(defaultEvidence(intentCode));
        }

        List<String> runtimeFacts = missingRuntimeFacts(intentCode, knownFacts);
        if (projectContext != null) {
            runtimeFacts.addAll(projectContext.missingRuntimeFacts());
        }
        return new EvidenceMatrix(distinct(evidenceTypes), distinct(discoveryTools), distinct(analysisTools),
                distinct(requiredConfig), distinct(runtimeFacts));
    }

    public List<ToolPlanCandidate> bootstrapCandidates(InvestigationContext context, List<com.codeagent.mcp.model.ToolDefinition> definitions) {
        EvidenceMatrix matrix = plan(context.intentLeaf(), context.projectContext(), context.knownFacts());
        List<String> available = definitions.stream().map(com.codeagent.mcp.model.ToolDefinition::name).toList();
        List<ToolPlanCandidate> candidates = new ArrayList<>();
        int priority = 1;
        for (String tool : matrix.preferredDiscoveryTools()) {
            if (available.contains(tool) && shouldPlan(tool, context.knownFacts())) {
                candidates.add(new ToolPlanCandidate(tool, "Discover runtime investigation facts",
                        Map.of(), outputFacts(definitions, tool), priority++, "Preferred discovery tool for intent."));
            }
        }
        for (String tool : matrix.preferredAnalysisTools()) {
            if (available.contains(tool) && shouldPlanAnalysis(tool, context.knownFacts())) {
                candidates.add(new ToolPlanCandidate(tool, "Collect configured platform evidence",
                        Map.of(), outputFacts(definitions, tool), priority++, "Preferred analysis tool for intent."));
            }
        }
        return candidates;
    }

    private boolean shouldPlan(String tool, Map<String, Object> facts) {
        if ("jenkins.find_recent_failed_builds".equals(tool)) {
            return !has(facts, "buildNumber");
        }
        if ("gitlab.find_merge_request_by_commit".equals(tool)) {
            return has(facts, "commitSha") && !has(facts, "mrIid");
        }
        if ("gitlab.find_recent_merge_requests".equals(tool)) {
            return !has(facts, "mrIid");
        }
        if ("log.search_errors".equals(tool)) {
            return !has(facts, "traceId");
        }
        if ("apm.search_traces".equals(tool)) {
            return !has(facts, "traceId");
        }
        return true;
    }

    private boolean shouldPlanAnalysis(String tool, Map<String, Object> facts) {
        if (tool.startsWith("jenkins.") && !tool.equals("jenkins.find_recent_failed_builds")) {
            return has(facts, "buildNumber");
        }
        if (tool.startsWith("gitlab.") && !tool.startsWith("gitlab.find_")) {
            return has(facts, "mrIid");
        }
        return true;
    }

    private List<String> outputFacts(List<com.codeagent.mcp.model.ToolDefinition> definitions, String tool) {
        return definitions.stream()
                .filter(definition -> definition.name().equals(tool))
                .findFirst()
                .map(com.codeagent.mcp.model.ToolDefinition::outputFacts)
                .orElse(List.of());
    }

    private List<String> defaultDiscovery(String intentCode) {
        if ("CI_FAILURE_ANALYSIS".equals(intentCode)) {
            return List.of("jenkins.find_recent_failed_builds", "gitlab.find_merge_request_by_commit");
        }
        if ("MR_IMPACT_ANALYSIS".equals(intentCode)) {
            return List.of("gitlab.find_merge_request_by_commit", "gitlab.find_recent_merge_requests");
        }
        if ("PROD_INCIDENT_ANALYSIS".equals(intentCode)) {
            return List.of("log.search_errors", "apm.search_traces");
        }
        return List.of();
    }

    private List<String> defaultAnalysis(String intentCode) {
        if ("CI_FAILURE_ANALYSIS".equals(intentCode)) {
            return List.of("jenkins.get_build_status", "jenkins.get_console_log_summary", "jenkins.get_test_report",
                    "gitlab.get_merge_request", "gitlab.get_merge_request_diff");
        }
        if ("MR_IMPACT_ANALYSIS".equals(intentCode)) {
            return List.of("gitlab.get_merge_request", "gitlab.get_merge_request_diff", "sonarqube.list_issues");
        }
        if ("QUALITY_RISK_ANALYSIS".equals(intentCode)) {
            return List.of("sonarqube.find_recent_issues", "sonarqube.list_issues", "sonarqube.get_quality_gate");
        }
        return List.of();
    }

    private List<String> defaultRequiredConfig(String intentCode) {
        if ("CI_FAILURE_ANALYSIS".equals(intentCode)) {
            return List.of("jenkinsJobName");
        }
        if ("MR_IMPACT_ANALYSIS".equals(intentCode)) {
            return List.of("gitlabProjectId");
        }
        if ("QUALITY_RISK_ANALYSIS".equals(intentCode)) {
            return List.of("sonarqubeProjectKey");
        }
        if ("PROD_INCIDENT_ANALYSIS".equals(intentCode)) {
            return List.of("logIndex", "apmServiceName");
        }
        return List.of();
    }

    private List<String> defaultEvidence(String intentCode) {
        if ("CI_FAILURE_ANALYSIS".equals(intentCode)) {
            return List.of("jenkins_build_status", "jenkins_console_log_summary", "jenkins_test_report");
        }
        if ("MR_IMPACT_ANALYSIS".equals(intentCode)) {
            return List.of("gitlab_merge_request", "gitlab_mr_diff");
        }
        if ("QUALITY_RISK_ANALYSIS".equals(intentCode)) {
            return List.of("sonarqube_issues", "sonarqube_quality_gate");
        }
        return List.of();
    }

    private List<String> missingRuntimeFacts(String intentCode, Map<String, Object> facts) {
        List<String> missing = new ArrayList<>();
        if ("CI_FAILURE_ANALYSIS".equals(intentCode)) {
            addIfMissing(missing, facts, "buildNumber");
            addIfMissing(missing, facts, "commitSha");
            addIfMissing(missing, facts, "mrIid");
            addIfMissing(missing, facts, "failedStage");
        }
        if ("MR_IMPACT_ANALYSIS".equals(intentCode)) {
            addIfMissing(missing, facts, "mrIid");
            addIfMissing(missing, facts, "commitSha");
        }
        if ("PROD_INCIDENT_ANALYSIS".equals(intentCode)) {
            addIfMissing(missing, facts, "traceId");
        }
        return missing;
    }

    private void addIfMissing(List<String> missing, Map<String, Object> facts, String fact) {
        if (!has(facts, fact)) {
            missing.add(fact);
        }
    }

    private boolean has(Map<String, Object> facts, String key) {
        Object value = facts == null ? null : facts.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }

    private List<String> distinct(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
