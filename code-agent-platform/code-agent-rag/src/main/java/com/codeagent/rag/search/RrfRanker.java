package com.codeagent.rag.search;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RrfRanker {
    private static final int K = 60;

    public Map<String, Double> fuse(List<String> vectorRanks, List<String> keywordRanks) {
        Map<String, Double> scores = new HashMap<>();
        add(scores, vectorRanks);
        add(scores, keywordRanks);
        return scores;
    }

    private void add(Map<String, Double> scores, List<String> ids) {
        for (int i = 0; i < ids.size(); i++) {
            scores.merge(ids.get(i), 1.0 / (K + i + 1), Double::sum);
        }
    }
}
