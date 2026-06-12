package com.codeagent.memory;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.memory.config.MemoryProperties;
import com.codeagent.memory.model.CoreMemoryItem;
import com.codeagent.storage.entity.MemoryCoreEntity;
import com.codeagent.storage.repository.MemoryCoreRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 核心记忆服务。
 *
 * <p>负责核心记忆的创建、查询，以及将持久化实体转换为运行时可使用的核心记忆项。
 * 核心记忆通常用于保存项目级或全局级的长期规则、偏好、约束等信息。</p>
 */
@Service
public class CoreMemoryService {
    private final MemoryCoreRepository repository;
    private final MemoryProperties properties;

    /**
     * 创建核心记忆服务实例。
     *
     * @param repository 核心记忆仓储，用于读写核心记忆数据
     * @param properties 记忆模块配置项，用于读取全局项目 Key、最大规则数量等配置
     */
    public CoreMemoryService(MemoryCoreRepository repository, MemoryProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * 创建一条核心记忆。
     *
     * <p>该方法使用默认标签、默认来源地址和默认状态创建核心记忆。</p>
     *
     * @param projectKey 项目标识
     * @param type       记忆类型
     * @param content    记忆内容
     * @param priority   优先级，值越大排序越靠前；为空时默认为 0
     * @return 创建并持久化后的核心记忆实体
     */
    public MemoryCoreEntity create(String projectKey, String type, String content, Integer priority) {
        return create(projectKey, type, content, List.of(), priority, null, "ACTIVE");
    }

    /**
     * 创建一条核心记忆。
     *
     * <p>创建前会校验项目 Key、类型和内容是否为空。
     * 标签会被序列化为 JSON 后存储，状态为空时默认使用 {@code ACTIVE}。</p>
     *
     * @param projectKey 项目标识
     * @param type       记忆类型
     * @param content    记忆内容
     * @param tags       标签列表，用于分类或检索；为空时使用空列表
     * @param priority   优先级，值越大排序越靠前；为空时默认为 0
     * @param sourceUri  来源 URI，可用于追踪记忆来源
     * @param status     记忆状态；为空时默认为 {@code ACTIVE}
     * @return 创建并持久化后的核心记忆实体
     * @throws BusinessException 当项目 Key、类型或内容为空时抛出
     */
    public MemoryCoreEntity create(String projectKey,
                                   String type,
                                   String content,
                                   List<String> tags,
                                   Integer priority,
                                   String sourceUri,
                                   String status) {
        validate(projectKey, type, content);
        MemoryCoreEntity entity = new MemoryCoreEntity();
        entity.projectKey = projectKey;
        entity.type = type;
        entity.content = content;
        entity.tagsJson = JsonSupport.toJson(tags == null ? List.of() : tags);
        entity.status = status == null || status.isBlank() ? "ACTIVE" : status;
        entity.sourceUri = sourceUri;
        entity.priority = priority == null ? 0 : priority;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = entity.createdAt;
        return repository.save(entity);
    }

    /**
     * 查询指定项目下的全部核心记忆。
     *
     * <p>结果按照优先级倒序、ID 倒序排列。</p>
     *
     * @param projectKey 项目标识
     * @return 指定项目下的核心记忆实体列表
     */
    public List<MemoryCoreEntity> list(String projectKey) {
        return repository.findByProjectKeyOrderByPriorityDescIdDesc(projectKey);
    }

    /**
     * 加载运行时常驻规则。
     *
     * <p>常驻规则由全局项目和当前项目下状态为 {@code ACTIVE} 的核心记忆组成。
     * 结果按照优先级倒序、ID 倒序排列，并根据配置限制最大数量。</p>
     *
     * @param projectKey 当前项目标识
     * @return 可供运行时使用的核心记忆项列表
     */
    public List<CoreMemoryItem> loadResidentRules(String projectKey) {
        List<String> keys = List.of(properties.getGlobalProjectKey(), projectKey);
        return repository.findByProjectKeyInAndStatusOrderByPriorityDescIdDesc(keys, "ACTIVE").stream()
                .limit(Math.max(1, properties.getMaxCoreRules()))
                .map(this::toItem)
                .toList();
    }

    /**
     * 将核心记忆持久化实体转换为运行时核心记忆项。
     *
     * @param entity 核心记忆持久化实体
     * @return 核心记忆项
     */
    private CoreMemoryItem toItem(MemoryCoreEntity entity) {
        return new CoreMemoryItem(entity.id, entity.projectKey, entity.type, entity.content,
                readTags(entity.tagsJson), entity.priority == null ? 0 : entity.priority, entity.sourceUri);
    }

    /**
     * 读取标签 JSON 并转换为字符串列表。
     *
     * <p>当标签 JSON 为空或解析失败时，返回空列表，避免异常影响核心记忆加载。</p>
     *
     * @param tagsJson 标签 JSON 字符串
     * @return 标签列表
     */
    private List<String> readTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            return JsonSupport.mapper().readValue(tagsJson,
                    JsonSupport.mapper().getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 校验创建核心记忆所需的必要字段。
     *
     * @param projectKey 项目标识
     * @param type       记忆类型
     * @param content    记忆内容
     * @throws BusinessException 当项目 Key、类型或内容为空时抛出
     */
    private void validate(String projectKey, String type, String content) {
        if (projectKey == null || projectKey.isBlank()) {
            throw new BusinessException("MEMORY_PROJECT_KEY_REQUIRED", "Memory projectKey must not be blank.");
        }
        if (type == null || type.isBlank()) {
            throw new BusinessException("MEMORY_TYPE_REQUIRED", "Core memory type must not be blank.");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("MEMORY_CONTENT_REQUIRED", "Core memory content must not be blank.");
        }
    }
}