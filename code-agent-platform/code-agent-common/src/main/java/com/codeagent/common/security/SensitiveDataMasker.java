package com.codeagent.common.security;

import java.util.List;
import java.util.regex.Pattern;

public final class SensitiveDataMasker {
    private static final String MASK = "$1***MASKED***";
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[A-Za-z0-9._\\-]+"),
            Pattern.compile("(?i)(private-token\\s*[:=]\\s*)[A-Za-z0-9._\\-]+"),
            Pattern.compile("(?i)(token\\s*[:=]\\s*)[A-Za-z0-9._\\-]+"),
            Pattern.compile("(?i)(secret\\s*[:=]\\s*)[^\\s,;]+"),
            Pattern.compile("(?i)(api[-_ ]?key\\s*[:=]\\s*)[A-Za-z0-9._\\-]+"),
            Pattern.compile("(?i)(access[-_ ]?key\\s*[:=]\\s*)[A-Za-z0-9._\\-]+"),
            Pattern.compile("(?i)(refresh[-_ ]?token\\s*[:=]\\s*)[A-Za-z0-9._\\-]+"),
            Pattern.compile("(?i)(password\\s*[:=]\\s*)[^\\s,;]+"),
            Pattern.compile("(?i)(cookie\\s*[:=]\\s*)[^\\n]+"),
            Pattern.compile("(?s)(-----BEGIN [A-Z ]*PRIVATE KEY-----).*?(-----END [A-Z ]*PRIVATE KEY-----)"),
            Pattern.compile("([A-Za-z0-9._%+-]{2})[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})"),
            Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})"),
            Pattern.compile("((?:10|172\\.(?:1[6-9]|2\\d|3[0-1])|192\\.168)\\.\\d{1,3}\\.)\\d{1,3}")
    );

    private SensitiveDataMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = text;
        for (Pattern pattern : PATTERNS) {
            masked = pattern.matcher(masked).replaceAll(MASK);
        }
        return masked;
    }
}
