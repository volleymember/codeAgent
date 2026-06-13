package com.codeagent.core.understanding;

import java.time.Instant;
import java.util.List;

public record ResolvedTimeRange(
        Instant startTime,
        Instant endTime,
        int hours,
        String source,
        List<String> warnings
) {
    public ResolvedTimeRange {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
