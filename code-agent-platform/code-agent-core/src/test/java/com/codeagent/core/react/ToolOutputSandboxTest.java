package com.codeagent.core.react;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.mcp.model.ToolCallResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolOutputSandboxTest {
    private final ToolOutputSandbox sandbox = new ToolOutputSandbox();

    @Test
    void redactsSensitiveValuesBeforeCompression() {
        ToolCallResult result = new ToolCallResult("jenkins.get_console_log_summary", "SUCCESS",
                "password=abc token=tok authorization: Bearer secret cookie=session apiKey=key user ab@example.com",
                "raw://1", List.of(), null, 1);

        CompressedToolObservation observation = sandbox.compress(result);

        assertThat(observation.compressedText()).contains("***MASKED***");
        assertThat(observation.compressedText()).doesNotContain("password=abc", "token=tok",
                "Bearer secret", "cookie=session", "ab@example.com");
        assertThat(observation.redactionCount()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void compressesLargeTextAndExtractsFacts() {
        String repeated = ("java.lang.IllegalStateException: boom\n"
                + "Caused by: PaymentException\n"
                + "at com.acme.PaymentService.pay(PaymentService.java:42)\n"
                + "commit abcdef123456 branch: feature/pay\n").repeat(200);
        ToolCallResult result = new ToolCallResult("jenkins.get_console_log_summary", "SUCCESS",
                repeated, "raw://1", List.of(new EvidenceItem("jenkins_console_log_summary", "log",
                "PaymentService.java:42 failed at commit abcdef123456", 0.9, "uri", "raw", Map.of())), null, 1);

        CompressedToolObservation observation = sandbox.compress(result);

        assertThat(observation.compressedSize()).isLessThan(observation.rawSize());
        assertThat(observation.extractedFacts()).containsEntry("exceptionName", "IllegalStateException");
        assertThat(observation.extractedFacts()).containsEntry("commitSha", "abcdef123456");
        assertThat(observation.extractedFacts()).containsEntry("filePath", "PaymentService.java");
        assertThat(observation.extractedFacts()).containsEntry("lineNumber", 42);
    }
}
