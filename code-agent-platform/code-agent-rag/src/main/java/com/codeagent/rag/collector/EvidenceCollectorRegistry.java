package com.codeagent.rag.collector;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.model.SourceSystem;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class EvidenceCollectorRegistry {
    private final Map<SourceSystem, EvidenceCollector> collectors = new EnumMap<>(SourceSystem.class);

    public EvidenceCollectorRegistry(List<EvidenceCollector> collectorList) {
        for (EvidenceCollector collector : collectorList) {
            collectors.put(collector.sourceSystem(), collector);
        }
    }

    public EvidenceCollector get(SourceSystem sourceSystem) {
        EvidenceCollector collector = collectors.get(sourceSystem);
        if (collector == null) {
            throw new BusinessException("EVIDENCE_COLLECTOR_NOT_FOUND", "No evidence collector for sourceSystem: " + sourceSystem);
        }
        return collector;
    }
}
