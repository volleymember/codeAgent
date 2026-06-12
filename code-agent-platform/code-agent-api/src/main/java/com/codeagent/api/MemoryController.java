package com.codeagent.api;

import com.codeagent.common.api.ApiResponse;
import com.codeagent.common.exception.BusinessException;
import com.codeagent.memory.CoreMemoryService;
import com.codeagent.memory.EpisodicMemoryService;
import com.codeagent.memory.MemoryCenterService;
import com.codeagent.memory.MemoryRecallAuditService;
import com.codeagent.memory.WorkingMemoryService;
import com.codeagent.memory.model.AgentMemoryNote;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.memory.model.MemoryCenterStats;
import com.codeagent.memory.model.MemoryEpisodeMatch;
import com.codeagent.memory.model.MemoryFeedbackRequest;
import com.codeagent.memory.model.MemoryRecallRequest;
import com.codeagent.storage.entity.MemoryCoreEntity;
import com.codeagent.storage.entity.MemoryEpisodeEntity;
import com.codeagent.storage.entity.MemoryRecallRecordEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {
    private final CoreMemoryService coreMemoryService;
    private final EpisodicMemoryService episodicMemoryService;
    private final WorkingMemoryService workingMemoryService;
    private final MemoryCenterService memoryCenterService;
    private final MemoryRecallAuditService memoryRecallAuditService;

    public MemoryController(CoreMemoryService coreMemoryService, EpisodicMemoryService episodicMemoryService,
                            WorkingMemoryService workingMemoryService,
                            MemoryCenterService memoryCenterService,
                            MemoryRecallAuditService memoryRecallAuditService) {
        this.coreMemoryService = coreMemoryService;
        this.episodicMemoryService = episodicMemoryService;
        this.workingMemoryService = workingMemoryService;
        this.memoryCenterService = memoryCenterService;
        this.memoryRecallAuditService = memoryRecallAuditService;
    }

    @PostMapping("/core")
    public ApiResponse<MemoryCoreEntity> createCore(@RequestBody CoreMemoryRequest request) {
        return ApiResponse.success(coreMemoryService.create(request.projectKey(), request.type(), request.content(),
                request.tags(), request.priority(), request.sourceUri(), request.status()));
    }

    @GetMapping("/core")
    public ApiResponse<List<MemoryCoreEntity>> listCore(@RequestParam String projectKey) {
        return ApiResponse.success(coreMemoryService.list(projectKey));
    }

    @PostMapping("/episode")
    public ApiResponse<MemoryEpisodeEntity> createEpisode(@RequestBody EpisodeMemoryRequest request) {
        return ApiResponse.success(episodicMemoryService.create(request.projectKey(), request.symptoms(),
                request.rootCause(), request.fix(), request.verifiedBy(), request.sourceUri(), request.reliability(),
                request.tags(), request.confidenceScore()));
    }

    @PostMapping("/episode/search")
    public ApiResponse<List<MemoryEpisodeMatch>> searchEpisode(@RequestBody MemoryRecallRequest request) {
        return ApiResponse.success(episodicMemoryService.recall(request));
    }

    @PostMapping("/episode/{episodeId}/feedback")
    public ApiResponse<MemoryEpisodeEntity> feedback(@PathVariable String episodeId,
                                                     @RequestBody MemoryFeedbackRequest request) {
        return ApiResponse.success(memoryCenterService.feedback(episodeId, request));
    }

    @PostMapping("/center/resolve")
    public ApiResponse<MemoryCenterContext> resolve(@RequestBody MemoryRecallRequest request) {
        return ApiResponse.success(memoryCenterService.buildContext(request));
    }

    @GetMapping("/center/stats")
    public ApiResponse<MemoryCenterStats> stats(@RequestParam String projectKey) {
        return ApiResponse.success(memoryCenterService.stats(projectKey));
    }

    @GetMapping("/center/recalls")
    public ApiResponse<List<MemoryRecallRecordEntity>> recalls(@RequestParam(required = false) String projectKey,
                                                               @RequestParam(required = false) String taskNo,
                                                               @RequestParam(required = false) String sessionId) {
        if (taskNo != null && !taskNo.isBlank()) {
            return ApiResponse.success(memoryRecallAuditService.byTask(taskNo));
        }
        if (sessionId != null && !sessionId.isBlank()) {
            return ApiResponse.success(memoryRecallAuditService.bySession(sessionId));
        }
        if (projectKey == null || projectKey.isBlank()) {
            throw new BusinessException("MEMORY_RECALL_QUERY_REQUIRED", "projectKey, taskNo or sessionId is required.");
        }
        return ApiResponse.success(memoryRecallAuditService.byProject(projectKey));
    }

    @PostMapping("/working/{sessionId}/notes")
    public ApiResponse<AgentMemoryNote> appendNote(@PathVariable String sessionId, @RequestBody AgentNoteRequest request) {
        return ApiResponse.success(memoryCenterService.appendAgentNote(sessionId, request.agentName(), request.phase(),
                request.note(), request.metadata()));
    }

    @GetMapping("/working/{sessionId}")
    public ApiResponse<Map<String, Object>> working(@PathVariable String sessionId) {
        return ApiResponse.success(Map.of("sessionId", sessionId, "context", workingMemoryService.getMap(sessionId)));
    }

    public record CoreMemoryRequest(String projectKey, String type, String content, List<String> tags,
                                    Integer priority, String sourceUri, String status) {
    }

    public record EpisodeMemoryRequest(String projectKey, List<String> symptoms, String rootCause,
                                       String fix, String verifiedBy, String sourceUri, String reliability,
                                       List<String> tags, Double confidenceScore) {
    }

    public record AgentNoteRequest(String agentName, String phase, String note, Map<String, Object> metadata) {
    }
}
