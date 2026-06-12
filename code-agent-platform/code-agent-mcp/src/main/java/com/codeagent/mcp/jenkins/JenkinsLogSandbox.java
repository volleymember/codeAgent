package com.codeagent.mcp.jenkins;

import com.codeagent.common.security.SensitiveDataMasker;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public final class JenkinsLogSandbox {
    private JenkinsLogSandbox() {
    }

    public static String summarize(String consoleLog) {
        if (consoleLog == null || consoleLog.isBlank()) {
            return "Console log is empty.";
        }
        String summary = Arrays.stream(consoleLog.split("\\R"))
                .filter(JenkinsLogSandbox::importantLine)
                .limit(80)
                .collect(Collectors.joining("\n"));
        if (summary.isBlank()) {
            summary = Arrays.stream(consoleLog.split("\\R")).skip(Math.max(0, consoleLog.split("\\R").length - 80L))
                    .collect(Collectors.joining("\n"));
        }
        return SensitiveDataMasker.mask(summary);
    }

    private static boolean importantLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("error")
                || lower.contains("failed")
                || lower.contains("failure")
                || lower.contains("exception")
                || lower.contains("assert")
                || lower.contains("caused by");
    }
}
