package com.codeagent.rag.rerank;

import com.codeagent.rag.model.EvidenceType;
import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.search.RagSearchResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceRerankerTest {
    private final EvidenceReranker reranker = new EvidenceReranker(
            new SourcePriorityScorer(),
            new SymbolMatchScorer(),
            new LocationMatchScorer(),
            new FreshnessScorer(),
            new FeedbackScorer()
    );

    @Test
    void reranksByWeightedFormulaAndTruncatesTopK() {
        RagSearchRequest request = request("AuthService timeout ERR_AUTH line 42", 1);
        RagSearchResult strong = result("chunk-1", EvidenceType.JAVA_CODE.name(), "AuthService",
                "timeout ERR_AUTH login", 40, 45, LocalDateTime.now().minusHours(3),
                0.8, 0.7, 0.8);
        RagSearchResult weak = result("chunk-2", EvidenceType.DOCUMENT.name(), "OpsGuide",
                "deployment note", 900, 980, LocalDateTime.now().minusDays(220),
                0.75, 0.1, 0.0);

        List<RagSearchResult> results = reranker.rerank(request, List.of(weak, strong), 1);

        assertThat(results).hasSize(1);
        RagSearchResult top = results.getFirst();
        assertThat(top.chunkId()).isEqualTo("chunk-1");
        assertThat(top.score()).isBetween(0.0, 1.0);
        assertThat(top.denseScore()).isEqualTo(0.8);
        assertThat(top.keywordScore()).isEqualTo(1.0);
        assertThat(top.feedbackScore()).isEqualTo(0.8);
        assertThat(top.matchReason())
                .contains("向量语义匹配")
                .contains("关键词命中")
                .contains("符号/异常关键字匹配")
                .contains("代码位置相关")
                .contains("finalScore=");
    }

    @Test
    void returnsAtLeastOneResultWhenTopKIsZero() {
        RagSearchRequest request = request("OrderController", 0);
        RagSearchResult candidate = result("chunk-3", EvidenceType.JAVA_CODE.name(), "OrderController",
                "order controller", 1, 18, LocalDateTime.now(), 0.5, 2.0, 0.5);

        List<RagSearchResult> results = reranker.rerank(request, List.of(candidate), 0);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().score()).isBetween(0.0, 1.0);
        assertThat(results.getFirst().keywordScore()).isEqualTo(1.0);
    }

    private RagSearchRequest request(String query, int topK) {
        return new RagSearchRequest(
                "project-a",
                null,
                "main",
                null,
                null,
                List.of(EvidenceType.JAVA_CODE),
                null,
                null,
                List.of(),
                query,
                topK,
                20,
                20
        );
    }

    private RagSearchResult result(String chunkId,
                                   String evidenceType,
                                   String symbolName,
                                   String keywords,
                                   Integer lineStart,
                                   Integer lineEnd,
                                   LocalDateTime createdAt,
                                   double denseScore,
                                   double keywordScore,
                                   double feedbackScore) {
        return new RagSearchResult(
                chunkId,
                "evidence-" + chunkId,
                "GITLAB",
                evidenceType,
                symbolName + ".java",
                "public class " + symbolName + " { throw new RuntimeException(\"ERR_AUTH timeout\"); }",
                "gitlab://project-a/" + chunkId,
                "https://gitlab.example.com/project-a/" + chunkId,
                "src/main/java/" + symbolName + ".java",
                lineStart + "-" + lineEnd,
                symbolName,
                keywords,
                lineStart,
                lineEnd,
                createdAt,
                0.0,
                denseScore,
                keywordScore,
                feedbackScore,
                "pre-rerank"
        );
    }
}
