package com.codeagent.core.intent;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.core.intent.dto.IntentLeafView;
import com.codeagent.core.intent.dto.IntentNodeRequest;
import com.codeagent.core.intent.dto.IntentTreeCreateRequest;
import com.codeagent.core.intent.dto.IntentTreeSnapshot;
import com.codeagent.storage.entity.IntentNodeEntity;
import com.codeagent.storage.entity.IntentTreeEntity;
import com.codeagent.storage.repository.IntentNodeRepository;
import com.codeagent.storage.repository.IntentTreeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 意图树服务。
 *
 * <p>该服务负责维护 Agent 的意图树，包括创建意图树、创建/更新节点、激活版本、
 * 查询当前激活意图树，以及导出可用于意图分类的叶子节点视图。</p>
 *
 * <p>意图树采用版本化管理方式，同一个 treeCode 可以存在多个 version，
 * 但同一时间通常只有一个 ACTIVE 版本。激活新版本时，会自动将同 treeCode 下其他 ACTIVE 版本置为 DISABLED。</p>
 *
 * <p>节点分为三类：</p>
 * <ul>
 *     <li>ROOT：根节点</li>
 *     <li>CATEGORY：分类节点</li>
 *     <li>LEAF：叶子节点，通常代表一个可被分类器选中的具体意图</li>
 * </ul>
 */
@Service
public class IntentTreeService {

    /**
     * 意图树草稿状态。
     */
    public static final String STATUS_DRAFT = "DRAFT";

    /**
     * 意图树启用状态。
     */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /**
     * 意图树禁用状态。
     */
    public static final String STATUS_DISABLED = "DISABLED";

    /**
     * 根节点类型。
     */
    public static final String NODE_ROOT = "ROOT";

    /**
     * 分类节点类型。
     */
    public static final String NODE_CATEGORY = "CATEGORY";

    /**
     * 叶子节点类型。
     *
     * <p>叶子节点会被转换为 IntentLeafView，作为意图分类候选项。</p>
     */
    public static final String NODE_LEAF = "LEAF";

    /**
     * 意图树仓储。
     */
    private final IntentTreeRepository treeRepository;

    /**
     * 意图节点仓储。
     */
    private final IntentNodeRepository nodeRepository;

    /**
     * 创建意图树服务。
     *
     * @param treeRepository 意图树仓储
     * @param nodeRepository 意图节点仓储
     */
    public IntentTreeService(IntentTreeRepository treeRepository, IntentNodeRepository nodeRepository) {
        this.treeRepository = treeRepository;
        this.nodeRepository = nodeRepository;
    }

    /**
     * 创建意图树版本。
     *
     * <p>如果请求中未指定 version，则自动取当前 treeCode 下最大版本号 + 1。
     * 如果指定版本已存在，则抛出业务异常，避免覆盖已有意图树版本。</p>
     *
     * @param request 创建意图树请求
     * @return 创建后的意图树实体
     * @throws BusinessException 当同 treeCode、version 的意图树已存在时抛出
     */
    @Transactional
    public IntentTreeEntity createTree(IntentTreeCreateRequest request) {
        int version = request.version() == null ? nextVersion(request.treeCode()) : Math.max(1, request.version());

        treeRepository.findByTreeCodeAndVersion(request.treeCode(), version).ifPresent(existing -> {
            throw new BusinessException("INTENT_TREE_EXISTS",
                    "Intent tree already exists: %s@%d".formatted(request.treeCode(), version));
        });

        IntentTreeEntity entity = new IntentTreeEntity();
        entity.treeCode = request.treeCode();
        entity.treeName = request.treeName();
        entity.version = version;
        entity.status = STATUS_DRAFT;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = entity.createdAt;

        return treeRepository.save(entity);
    }

    /**
     * 在指定意图树中创建节点。
     *
     * <p>创建节点前会先解析目标意图树版本、规范化节点类型、校验父节点是否存在，
     * 并检查同一意图树版本下 nodeCode 是否重复。</p>
     *
     * @param treeCode 意图树编码
     * @param request  节点创建请求
     * @return 创建后的节点实体
     * @throws BusinessException 当意图树不存在、节点重复、父节点非法或节点类型非法时抛出
     */
    @Transactional
    public IntentNodeEntity createNode(String treeCode, IntentNodeRequest request) {
        IntentTreeEntity tree = resolveTree(treeCode, request.version());
        String nodeType = normalizeNodeType(request.nodeType());

        validateParent(tree, request.parentCode(), request.nodeCode());

        nodeRepository.findByTreeCodeAndVersionAndNodeCode(tree.treeCode, tree.version, request.nodeCode())
                .ifPresent(existing -> {
                    throw new BusinessException("INTENT_NODE_EXISTS",
                            "Intent node already exists: %s@%d/%s".formatted(tree.treeCode, tree.version, request.nodeCode()));
                });

        IntentNodeEntity entity = new IntentNodeEntity();
        entity.treeCode = tree.treeCode;
        entity.version = tree.version;
        entity.nodeCode = request.nodeCode();

        apply(entity, request, nodeType);

        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = entity.createdAt;

        return nodeRepository.save(entity);
    }

    /**
     * 更新指定意图节点。
     *
     * <p>如果 request.nodeCode 为空，则保持原 nodeCode；
     * 如果 request.nodeType 为空，则保持原 nodeType。
     * 更新前会重新校验父节点关系，防止节点指向自身或不存在的父节点。</p>
     *
     * @param treeCode 意图树编码
     * @param nodeCode 待更新节点编码
     * @param request  节点更新请求
     * @return 更新后的节点实体
     * @throws BusinessException 当意图树或节点不存在、父节点非法、节点类型非法时抛出
     */
    @Transactional
    public IntentNodeEntity updateNode(String treeCode, String nodeCode, IntentNodeRequest request) {
        IntentTreeEntity tree = resolveTree(treeCode, request.version());

        IntentNodeEntity entity = nodeRepository.findByTreeCodeAndVersionAndNodeCode(tree.treeCode, tree.version, nodeCode)
                .orElseThrow(() -> new BusinessException("INTENT_NODE_NOT_FOUND",
                        "Intent node not found: %s@%d/%s".formatted(tree.treeCode, tree.version, nodeCode)));

        String nextCode = hasText(request.nodeCode()) ? request.nodeCode() : nodeCode;
        String nodeType = hasText(request.nodeType()) ? normalizeNodeType(request.nodeType()) : entity.nodeType;

        validateParent(tree, request.parentCode(), nextCode);

        entity.nodeCode = nextCode;
        apply(entity, request, nodeType);
        entity.updatedAt = LocalDateTime.now();

        return nodeRepository.save(entity);
    }

    /**
     * 激活指定意图树版本。
     *
     * <p>激活前会校验该版本下是否至少存在一个启用的叶子节点。
     * 激活成功后，同一 treeCode 下其他 ACTIVE 版本会被自动置为 DISABLED，
     * 从而保证同一意图树编码只有一个当前生效版本。</p>
     *
     * @param treeCode 意图树编码
     * @param version  意图树版本
     * @return 激活后的意图树实体
     * @throws BusinessException 当意图树不存在或没有启用的叶子节点时抛出
     */
    @Transactional
    public IntentTreeEntity activate(String treeCode, int version) {
        IntentTreeEntity tree = treeRepository.findByTreeCodeAndVersion(treeCode, version)
                .orElseThrow(() -> new BusinessException("INTENT_TREE_NOT_FOUND",
                        "Intent tree not found: %s@%d".formatted(treeCode, version)));

        boolean hasLeaf = nodeRepository.existsByTreeCodeAndVersionAndNodeTypeAndEnabledTrue(treeCode, version, NODE_LEAF);
        if (!hasLeaf) {
            throw new BusinessException("INTENT_TREE_HAS_NO_LEAF",
                    "Cannot activate intent tree without enabled leaf nodes: %s@%d".formatted(treeCode, version));
        }

        // 禁用同一 treeCode 下其他已激活版本，确保只有当前版本生效。
        for (IntentTreeEntity active : treeRepository.findByTreeCodeAndStatus(treeCode, STATUS_ACTIVE)) {
            if (!Objects.equals(active.id, tree.id)) {
                active.status = STATUS_DISABLED;
                active.updatedAt = LocalDateTime.now();
                treeRepository.save(active);
            }
        }

        tree.status = STATUS_ACTIVE;
        tree.updatedAt = LocalDateTime.now();

        return treeRepository.save(tree);
    }

    /**
     * 查询所有当前激活的意图树快照。
     *
     * <p>快照包含意图树实体以及该版本下的全部节点，
     * 节点按照 sortOrder 和 id 升序排列。</p>
     *
     * @return 当前激活的意图树快照列表
     */
    @Transactional(readOnly = true)
    public List<IntentTreeSnapshot> activeTrees() {
        return treeRepository.findByStatusOrderByUpdatedAtDesc(STATUS_ACTIVE).stream()
                .map(tree -> new IntentTreeSnapshot(tree,
                        nodeRepository.findByTreeCodeAndVersionOrderBySortOrderAscIdAsc(tree.treeCode, tree.version)))
                .toList();
    }

    /**
     * 查询指定意图树版本快照。
     *
     * @param treeCode 意图树编码
     * @param version  意图树版本
     * @return 指定版本的意图树快照
     * @throws BusinessException 当意图树版本不存在时抛出
     */
    @Transactional(readOnly = true)
    public IntentTreeSnapshot getVersion(String treeCode, int version) {
        IntentTreeEntity tree = treeRepository.findByTreeCodeAndVersion(treeCode, version)
                .orElseThrow(() -> new BusinessException("INTENT_TREE_NOT_FOUND",
                        "Intent tree not found: %s@%d".formatted(treeCode, version)));

        return new IntentTreeSnapshot(tree,
                nodeRepository.findByTreeCodeAndVersionOrderBySortOrderAscIdAsc(treeCode, version));
    }

    /**
     * 查询所有激活意图树中的启用叶子节点。
     *
     * <p>该方法通常供 IntentClassifier 使用。
     * 每个 LEAF 节点会被转换为 IntentLeafView，其中包含路径、关键词、示例查询、
     * 默认时间范围、允许工具类型和必需证据类型等信息。</p>
     *
     * @return 当前可用于意图分类的叶子节点视图列表
     */
    @Transactional(readOnly = true)
    public List<IntentLeafView> activeLeaves() {
        List<IntentLeafView> leaves = new ArrayList<>();

        for (IntentTreeEntity tree : treeRepository.findByStatusOrderByUpdatedAtDesc(STATUS_ACTIVE)) {
            List<IntentNodeEntity> nodes = nodeRepository.findByTreeCodeAndVersionOrderBySortOrderAscIdAsc(
                    tree.treeCode, tree.version);

            // 构建 nodeCode 到节点实体的映射，用于后续计算节点路径。
            Map<String, IntentNodeEntity> byCode = new LinkedHashMap<>();
            nodes.forEach(node -> byCode.put(node.nodeCode, node));

            nodes.stream()
                    .filter(node -> NODE_LEAF.equals(node.nodeType))
                    .filter(node -> Boolean.TRUE.equals(node.enabled))
                    .sorted(Comparator.comparingInt(node -> node.sortOrder == null ? 0 : node.sortOrder))
                    .map(node -> leaf(tree, node, byCode))
                    .forEach(leaves::add);
        }

        return leaves;
    }

    /**
     * 将意图叶子节点转换为 IntentLeafView。
     *
     * @param tree   节点所属意图树
     * @param node   叶子节点实体
     * @param byCode 当前意图树版本下的节点索引
     * @return 叶子节点视图
     */
    private IntentLeafView leaf(IntentTreeEntity tree, IntentNodeEntity node, Map<String, IntentNodeEntity> byCode) {
        return new IntentLeafView(
                tree.treeCode,
                tree.version,
                node.nodeCode,
                path(node, byCode),
                node.nodeName,
                node.description,
                readList(node.keywordsJson),
                readList(node.exampleQueriesJson),
                node.defaultTimeRangeHours,
                readList(node.allowedToolTypesJson),
                readList(node.requiredEvidenceTypesJson),
                readList(node.preferredDiscoveryToolsJson),
                readList(node.preferredAnalysisToolsJson),
                readList(node.requiredConfigFieldsJson)
        );
    }

    /**
     * 计算节点路径。
     *
     * <p>从当前节点开始沿 parentCode 向上查找父节点，最终拼接成 ROOT/CATEGORY/LEAF 形式的路径。
     * guard 用于防止异常数据导致无限循环。</p>
     *
     * @param node   当前节点
     * @param byCode 当前意图树版本下的节点索引
     * @return 节点路径
     */
    private String path(IntentNodeEntity node, Map<String, IntentNodeEntity> byCode) {
        List<String> parts = new ArrayList<>();
        IntentNodeEntity current = node;
        int guard = 0;

        while (current != null && guard++ < 32) {
            parts.addFirst(current.nodeCode);
            current = hasText(current.parentCode) ? byCode.get(current.parentCode) : null;
        }

        return String.join("/", parts);
    }

    /**
     * 将请求中的节点字段应用到实体。
     *
     * <p>列表类字段会序列化为 JSON 字符串存储；
     * enabled 为空时默认启用；
     * sortOrder 为空时默认为 0。</p>
     *
     * @param entity   待更新实体
     * @param request  节点请求
     * @param nodeType 规范化后的节点类型
     */
    private void apply(IntentNodeEntity entity, IntentNodeRequest request, String nodeType) {
        entity.parentCode = blankToNull(request.parentCode());
        entity.nodeName = request.nodeName();
        entity.nodeType = nodeType;
        entity.description = request.description();
        entity.keywordsJson = JsonSupport.toJson(safeList(request.keywords()));
        entity.exampleQueriesJson = JsonSupport.toJson(safeList(request.exampleQueries()));
        entity.defaultTimeRangeHours = request.defaultTimeRangeHours();
        entity.allowedToolTypesJson = JsonSupport.toJson(safeList(request.allowedToolTypes()));
        entity.requiredEvidenceTypesJson = JsonSupport.toJson(safeList(request.requiredEvidenceTypes()));
        entity.preferredDiscoveryToolsJson = JsonSupport.toJson(safeList(request.preferredDiscoveryTools()));
        entity.preferredAnalysisToolsJson = JsonSupport.toJson(safeList(request.preferredAnalysisTools()));
        entity.requiredConfigFieldsJson = JsonSupport.toJson(safeList(request.requiredConfigFields()));
        entity.enabled = request.enabled() == null || request.enabled();
        entity.sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
    }

    /**
     * 解析目标意图树版本。
     *
     * <p>如果请求指定了版本，则查询指定版本；
     * 如果未指定版本，则默认取该 treeCode 下版本号最大的意图树。</p>
     *
     * @param treeCode         意图树编码
     * @param requestedVersion 请求指定版本，可为空
     * @return 解析到的意图树实体
     * @throws BusinessException 当目标意图树不存在时抛出
     */
    private IntentTreeEntity resolveTree(String treeCode, Integer requestedVersion) {
        if (requestedVersion != null) {
            return treeRepository.findByTreeCodeAndVersion(treeCode, requestedVersion)
                    .orElseThrow(() -> new BusinessException("INTENT_TREE_NOT_FOUND",
                            "Intent tree not found: %s@%d".formatted(treeCode, requestedVersion)));
        }

        return treeRepository.findByTreeCodeOrderByVersionDesc(treeCode).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("INTENT_TREE_NOT_FOUND", "Intent tree not found: " + treeCode));
    }

    /**
     * 计算下一个意图树版本号。
     *
     * @param treeCode 意图树编码
     * @return 当前最大版本号 + 1；如果不存在历史版本，则返回 1
     */
    private int nextVersion(String treeCode) {
        return treeRepository.findByTreeCodeOrderByVersionDesc(treeCode).stream()
                .map(tree -> tree.version == null ? 0 : tree.version)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    /**
     * 校验父节点是否合法。
     *
     * <p>如果 parentCode 为空，表示当前节点没有父节点，直接通过；
     * 如果 parentCode 等于 nodeCode，则说明节点试图成为自己的父节点，应拒绝；
     * 否则检查父节点是否存在于同一意图树版本中。</p>
     *
     * @param tree       当前意图树
     * @param parentCode 父节点编码
     * @param nodeCode   当前节点编码
     * @throws BusinessException 当父节点非法或不存在时抛出
     */
    private void validateParent(IntentTreeEntity tree, String parentCode, String nodeCode) {
        if (!hasText(parentCode)) {
            return;
        }

        if (parentCode.equals(nodeCode)) {
            throw new BusinessException("INTENT_NODE_PARENT_INVALID", "Intent node cannot be its own parent.");
        }

        boolean exists = nodeRepository.findByTreeCodeAndVersionAndNodeCode(tree.treeCode, tree.version, parentCode).isPresent();
        if (!exists) {
            throw new BusinessException("INTENT_NODE_PARENT_NOT_FOUND", "Parent node not found: " + parentCode);
        }
    }

    /**
     * 规范化节点类型。
     *
     * <p>节点类型会被去除前后空白并转为大写。
     * 仅允许 ROOT、CATEGORY、LEAF 三种类型。</p>
     *
     * @param nodeType 原始节点类型
     * @return 规范化后的节点类型
     * @throws BusinessException 当节点类型不被支持时抛出
     */
    private String normalizeNodeType(String nodeType) {
        String normalized = nodeType == null ? "" : nodeType.trim().toUpperCase();

        if (!List.of(NODE_ROOT, NODE_CATEGORY, NODE_LEAF).contains(normalized)) {
            throw new BusinessException("INTENT_NODE_TYPE_INVALID", "Unsupported intent node type: " + nodeType);
        }

        return normalized;
    }

    /**
     * 清洗字符串列表。
     *
     * <p>会过滤 null 和空白字符串，对每个值 trim，并去重。</p>
     *
     * @param values 原始字符串列表
     * @return 清洗后的不可变风格列表
     */
    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * 从 JSON 字符串中读取字符串列表。
     *
     * <p>如果 JSON 为空或解析失败，则返回空列表，避免影响意图树查询。</p>
     *
     * @param json JSON 字符串
     * @return 字符串列表
     */
    private List<String> readList(String json) {
        if (!hasText(json)) {
            return List.of();
        }

        try {
            return JsonSupport.mapper().readValue(json,
                    JsonSupport.mapper().getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 判断字符串是否包含有效文本。
     *
     * @param value 待判断字符串
     * @return 非 null 且非空白时返回 true
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 将空白字符串转换为 null。
     *
     * @param value 原始字符串
     * @return trim 后的字符串；如果为空则返回 null
     */
    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
