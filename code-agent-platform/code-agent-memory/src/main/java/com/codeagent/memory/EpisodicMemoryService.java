package com.codeagent.memory;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.memory.config.MemoryProperties;
import com.codeagent.memory.model.MemoryEpisodeMatch;
import com.codeagent.memory.model.MemoryFeedbackRequest;
import com.codeagent.memory.model.MemoryRecallRequest;
import com.codeagent.storage.entity.MemoryEpisodeEntity;
import com.codeagent.storage.repository.MemoryEpisodeRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EpisodicMemoryService {
    private final MemoryEpisodeRepository repository;
    private final EpisodicMemoryScorer scorer;
    private final MemoryProperties properties;

    public EpisodicMemoryService(MemoryEpisodeRepository repository,
                                 EpisodicMemoryScorer scorer,
                                 MemoryProperties properties) {
        this.repository = repository;
        this.scorer = scorer;
        this.properties = properties;
    }

    public MemoryEpisodeEntity create(String projectKey, List<String> symptoms, String rootCause,
                                      String fix, String verifiedBy, String sourceUri, String reliability) {
        return create(projectKey, symptoms, rootCause, fix, verifiedBy, sourceUri, reliability, List.of(), null);
    }

    public MemoryEpisodeEntity create(String projectKey,
                                      List<String> symptoms,
                                      String rootCause,
                                      String fix,
                                      String verifiedBy,
                                      String sourceUri,
                                      String reliability,
                                      List<String> tags,
                                      Double confidenceScore) {
        validate(projectKey, symptoms, rootCause, fix);
        String signature = signature(projectKey, symptoms);
        MemoryEpisodeEntity entity = repository.findFirstByProjectKeyAndSymptomSignatureOrderByIdDesc(projectKey, signature)
                .orElseGet(MemoryEpisodeEntity::new);
        boolean existing = entity.id != null;
        if (!existing) {
            entity.episodeId = "EP-" + UUID.randomUUID();
            entity.createdAt = LocalDateTime.now();
            entity.hitCount = 0;
            entity.helpfulCount = 0;
            entity.unhelpfulCount = 0;
            entity.status = "ACTIVE";
        }
        entity.projectKey = projectKey;
        entity.symptomsJson = JsonSupport.toJson(symptoms == null ? List.of() : symptoms);
        entity.symptomSignature = signature;
        entity.rootCause = rootCause;
        entity.fixContent = fix;
        entity.verifiedBy = verifiedBy;
        entity.sourceUri = sourceUri;
        entity.reliability = reliability == null ? "candidate" : reliability;
        entity.tagsJson = JsonSupport.toJson(tags == null ? List.of() : tags);
        entity.confidenceScore = confidenceScore == null ? entity.confidenceScore : confidenceScore;
        entity.status = entity.status == null || entity.status.isBlank() ? "ACTIVE" : entity.status;
        entity.updatedAt = LocalDateTime.now();
        return repository.save(entity);
    }

    public List<MemoryEpisodeEntity> list(String projectKey) {
        return repository.findByProjectKeyOrderByIdDesc(projectKey);
    }

    public List<MemoryEpisodeMatch> recall(MemoryRecallRequest request) {
        if (request.projectKey() == null || request.projectKey().isBlank()) {
            throw new BusinessException("MEMORY_PROJECT_KEY_REQUIRED", "Memory recall projectKey must not be blank.");
        }
        List<String> projectKeys = List.of(properties.getGlobalProjectKey(), request.projectKey());
        List<MemoryEpisodeMatch> matches = repository.findTop100ByProjectKeyInOrderByUpdatedAtDesc(projectKeys).stream()
                .filter(episode -> episode.status == null || "ACTIVE".equalsIgnoreCase(episode.status))
                .map(episode -> toMatch(request, episode))
                .filter(match -> match.score() >= Math.max(0.0, properties.getMinRecallScore()))
                .sorted(Comparator.comparingDouble(MemoryEpisodeMatch::score).reversed())
                .limit(Math.max(1, request.limit()))
                .toList();
        markRecalled(matches);
        return matches;
    }

    public MemoryEpisodeEntity feedback(String episodeId, MemoryFeedbackRequest request) {
        if (episodeId == null || episodeId.isBlank()) {
            throw new BusinessException("MEMORY_EPISODE_ID_REQUIRED", "Memory episodeId must not be blank.");
        }
        MemoryEpisodeEntity entity = repository.findByEpisodeId(episodeId)
                .orElseThrow(() -> new BusinessException("MEMORY_EPISODE_NOT_FOUND", "Memory episode not found: " + episodeId));
        if (request.helpful()) {
            entity.helpfulCount = entity.helpfulCount == null ? 1 : entity.helpfulCount + 1;
            entity.confidenceScore = clamp((entity.confidenceScore == null ? 0.5 : entity.confidenceScore) + 0.08);
        } else {
            entity.unhelpfulCount = entity.unhelpfulCount == null ? 1 : entity.unhelpfulCount + 1;
            entity.confidenceScore = clamp((entity.confidenceScore == null ? 0.5 : entity.confidenceScore) - 0.12);
        }
        entity.reliability = reliability(entity);
        entity.status = status(entity);
        entity.feedbackJson = appendFeedback(entity.feedbackJson, request);
        entity.updatedAt = LocalDateTime.now();
        return repository.save(entity);
    }

    private MemoryEpisodeMatch toMatch(MemoryRecallRequest request, MemoryEpisodeEntity episode) {
        double score = scorer.score(request, episode);
        return new MemoryEpisodeMatch(
                episode.episodeId,
                episode.projectKey,
                scorer.readSymptoms(episode),
                episode.rootCause,
                episode.fixContent,
                episode.verifiedBy,
                episode.sourceUri,
                episode.reliability,
                score,
                scorer.reason(request, episode, score)
        );
    }

    private void markRecalled(List<MemoryEpisodeMatch> matches) {
        for (MemoryEpisodeMatch match : matches) {
            repository.findFirstByProjectKeyAndSymptomSignatureOrderByIdDesc(match.projectKey(), signature(match.projectKey(), match.symptoms()))
                    .ifPresent(entity -> {
                        entity.hitCount = entity.hitCount == null ? 1 : entity.hitCount + 1;
                        entity.lastRecalledAt = LocalDateTime.now();
                        entity.updatedAt = LocalDateTime.now();
                        repository.save(entity);
                    });
        }
    }

    private String reliability(MemoryEpisodeEntity entity) {
        int helpful = entity.helpfulCount == null ? 0 : entity.helpfulCount;
        int unhelpful = entity.unhelpfulCount == null ? 0 : entity.unhelpfulCount;
        double confidence = entity.confidenceScore == null ? 0.5 : entity.confidenceScore;
        if (helpful >= 3 && confidence >= 0.72 && helpful > unhelpful) {
            return "verified";
        }
        if (unhelpful >= 3 && unhelpful > helpful) {
            return "low_confidence";
        }
        return entity.reliability == null || entity.reliability.isBlank() ? "candidate" : entity.reliability;
    }

    private String status(MemoryEpisodeEntity entity) {
        int helpful = entity.helpfulCount == null ? 0 : entity.helpfulCount;
        int unhelpful = entity.unhelpfulCount == null ? 0 : entity.unhelpfulCount;
        if (unhelpful >= 5 && unhelpful >= helpful + 3) {
            return "ARCHIVED";
        }
        return "ACTIVE";
    }

    private String appendFeedback(String feedbackJson, MemoryFeedbackRequest request) {
        List<Map<String, Object>> feedback = new ArrayList<>();
        if (feedbackJson != null && !feedbackJson.isBlank()) {
            try {
                feedback.addAll(JsonSupport.mapper().readValue(feedbackJson,
                        JsonSupport.mapper().getTypeFactory().constructCollectionType(List.class, Map.class)));
            } catch (Exception ignored) {
                feedback.clear();
            }
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("helpful", request.helpful());
        item.put("agentName", request.agentName() == null ? "UNKNOWN" : request.agentName());
        item.put("reason", request.reason() == null ? "" : request.reason());
        item.put("createdAt", LocalDateTime.now().toString());
        feedback.add(item);
        if (feedback.size() > 20) {
            feedback = feedback.subList(feedback.size() - 20, feedback.size());
        }
        return JsonSupport.toJson(feedback);
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private void validate(String projectKey, List<String> symptoms, String rootCause, String fix) {
        if (projectKey == null || projectKey.isBlank()) {
            throw new BusinessException("MEMORY_PROJECT_KEY_REQUIRED", "Episode memory projectKey must not be blank.");
        }
        if (symptoms == null || symptoms.isEmpty()) {
            throw new BusinessException("MEMORY_SYMPTOMS_REQUIRED", "Episode memory symptoms must not be empty.");
        }
        if (rootCause == null || rootCause.isBlank()) {
            throw new BusinessException("MEMORY_ROOT_CAUSE_REQUIRED", "Episode memory rootCause must not be blank.");
        }
        if (fix == null || fix.isBlank()) {
            throw new BusinessException("MEMORY_FIX_REQUIRED", "Episode memory fix must not be blank.");
        }
    }

    private String signature(String projectKey, List<String> symptoms) {
        String joined = (projectKey + "::" + String.join("|", symptoms == null ? List.of() : symptoms)).toLowerCase();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("MEMORY_SIGNATURE_FAILED", "Failed to calculate memory episode signature.", e);
        }
    }
}
