package com.codeagent.core.understanding;

import com.codeagent.common.json.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;

public final class LlmJsonSupport {
    private LlmJsonSupport() {
    }

    public static JsonNode parseObject(String content) {
        try {
            return JsonSupport.mapper().readTree(extractObject(content));
        } catch (Exception e) {
            throw new IllegalArgumentException("LLM output is not valid JSON object.", e);
        }
    }

    private static String extractObject(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
