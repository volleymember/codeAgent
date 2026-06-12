package com.codeagent.core.agent;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.enums.ModelTaskType;
import com.codeagent.common.exception.BusinessException;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.core.context.EvidenceContextSandbox;
import com.codeagent.core.context.ManagedEvidenceContext;
import com.codeagent.memory.MemoryContextCompressor;
import com.codeagent.memory.model.CompressedMemoryContext;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.llm.client.LlmClient;
import com.codeagent.llm.config.LlmProperties;
import com.codeagent.llm.model.LlmRequest;
import com.codeagent.llm.model.TokenBudget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FinalReportAgent {
    private final LlmClient llmClient;
    private final EvidenceContextSandbox evidenceContextSandbox;
    private final LlmProperties llmProperties;
    private final MemoryContextCompressor memoryContextCompressor;
    private final FinalReportValidator finalReportValidator;

    public FinalReportAgent(LlmClient llmClient,
                            EvidenceContextSandbox evidenceContextSandbox,
                            LlmProperties llmProperties,
                            MemoryContextCompressor memoryContextCompressor,
                            FinalReportValidator finalReportValidator) {
        this.llmClient = llmClient;
        this.evidenceContextSandbox = evidenceContextSandbox;
        this.llmProperties = llmProperties;
        this.memoryContextCompressor = memoryContextCompressor;
        this.finalReportValidator = finalReportValidator;
    }

    public String generate(String taskNo, String sessionId, String taskType, List<EvidenceItem> evidence, Map<String, Object> critique) {
        return generate(taskNo, sessionId, taskType, evidence, critique, null);
    }

    public String generate(String taskNo,
                           String sessionId,
                           String taskType,
                           List<EvidenceItem> evidence,
                           Map<String, Object> critique,
                           MemoryCenterContext memoryContext) {
        if (evidence.isEmpty()) {
            throw new BusinessException("EVIDENCE_REQUIRED", "Cannot generate final report without evidence.");
        }
        String systemPrompt = """
                You are CodeAgent FinalReportAgent. Generate a concise Chinese engineering diagnosis as strict JSON.
                Every conclusion must be grounded in the provided Evidence Pack.
                Project core rules in Memory Center are mandatory constraints.
                Recalled historical bug episodes are references only; verify them against current evidence before using.
                Do not invent external platform facts. If evidence is insufficient, state the uncertainty explicitly.
                Output only this JSON shape:
                {
                  "suspectedCause": "Chinese root cause summary",
                  "confidence": 0.0,
                  "claims": [
                    {"claim": "Chinese evidence-backed claim", "evidenceRefs": ["TASK-...-E1"], "confidence": 0.0, "counterEvidence": []}
                  ],
                  "suggestedFix": "Chinese fix direction",
                  "suggestedTests": ["Chinese test recommendation"],
                  "toolSummary": ["tool or agent result summary"],
                  "uncertainties": ["unknown or unverified point"]
                }
                evidenceRefs must be copied exactly from evidencePack[].rawRef.
                """;
        ManagedEvidenceContext evidenceContext = evidenceContextSandbox.build(evidence);
        CompressedMemoryContext compressedMemory = memoryContextCompressor.compress(memoryContext);
        String userPrompt = JsonSupport.toJson(Map.of(
                "taskNo", taskNo,
                "taskType", taskType,
                "critique", critique,
                "contextPolicy", Map.of(
                        "strategy", "TOKEN_GOVERNED_EVIDENCE_CONTEXT",
                        "originalEvidenceCount", evidenceContext.originalEvidenceCount(),
                        "includedEvidenceCount", evidenceContext.includedEvidenceCount(),
                        "omittedEvidenceCount", evidenceContext.omittedEvidenceCount(),
                        "estimatedEvidenceTokens", evidenceContext.estimatedTokens(),
                        "budgetPolicy", evidenceContext.budgetPolicy()
                ),
                "evidencePack", evidenceContext.evidence(),
                "memoryCenter", compressedMemory
        ));
        String llmContent = llmClient.chat(new LlmRequest(taskNo, sessionId, ModelTaskType.FINAL_REPORT,
                systemPrompt, userPrompt, 3000, 0.2,
                new TokenBudget(llmProperties.getMaxInputTokens(),
                        llmProperties.getMaxOutputTokens(),
                        llmProperties.getMaxEvidenceTokens()))).content();
        return finalReportValidator.render(llmContent, evidence, critique, compressedMemory);
    }
}
