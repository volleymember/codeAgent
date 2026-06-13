package com.codeagent.core.intent.dto;

import com.codeagent.storage.entity.IntentNodeEntity;
import com.codeagent.storage.entity.IntentTreeEntity;

import java.util.List;

public record IntentTreeSnapshot(
        IntentTreeEntity tree,
        List<IntentNodeEntity> nodes
) {
    public IntentTreeSnapshot {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
