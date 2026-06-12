package com.codeagent.memory;

import com.codeagent.common.json.JsonSupport;
import com.codeagent.memory.config.MemoryProperties;
import com.codeagent.memory.model.AgentMemoryNote;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkingMemoryService {
    private final StringRedisTemplate redisTemplate;
    private final MemoryProperties properties;

    public WorkingMemoryService(StringRedisTemplate redisTemplate, MemoryProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void save(String sessionId, Map<String, Object> context) {
        redisTemplate.opsForValue().set(key(sessionId), JsonSupport.toJson(context),
                Duration.ofHours(Math.max(1, properties.getWorkingTtlHours())));
    }

    public String get(String sessionId) {
        return redisTemplate.opsForValue().get(key(sessionId));
    }

    public Map<String, Object> getMap(String sessionId) {
        String json = get(sessionId);
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return JsonSupport.mapper().readValue(json,
                    JsonSupport.mapper().getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public Map<String, Object> merge(String sessionId, Map<String, Object> patch) {
        Map<String, Object> current = getMap(sessionId);
        current.putAll(patch == null ? Map.of() : patch);
        current.put("updatedAt", LocalDateTime.now().toString());
        save(sessionId, current);
        return current;
    }

    public AgentMemoryNote appendAgentNote(String sessionId,
                                           String agentName,
                                           String phase,
                                           String note,
                                           Map<String, Object> metadata) {
        AgentMemoryNote agentNote = new AgentMemoryNote(agentName, phase, note, metadata, LocalDateTime.now());
        Map<String, Object> current = getMap(sessionId);
        List<Object> notes = new ArrayList<>();
        Object existing = current.get("agentNotes");
        if (existing instanceof List<?> list) {
            notes.addAll(list);
        }
        notes.add(Map.of(
                "agentName", agentNote.agentName(),
                "phase", agentNote.phase(),
                "note", agentNote.note(),
                "metadata", agentNote.metadata(),
                "createdAt", agentNote.createdAt().toString()
        ));
        int maxNotes = Math.max(1, properties.getMaxAgentNotes());
        if (notes.size() > maxNotes) {
            notes = notes.subList(notes.size() - maxNotes, notes.size());
        }
        current.put("agentNotes", notes);
        current.put("updatedAt", LocalDateTime.now().toString());
        save(sessionId, current);
        return agentNote;
    }

    private String key(String sessionId) {
        return "code-agent:working-memory:" + sessionId;
    }
}
