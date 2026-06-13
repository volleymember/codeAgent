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

@Service
public class IntentTreeService {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String NODE_ROOT = "ROOT";
    public static final String NODE_CATEGORY = "CATEGORY";
    public static final String NODE_LEAF = "LEAF";

    private final IntentTreeRepository treeRepository;
    private final IntentNodeRepository nodeRepository;

    public IntentTreeService(IntentTreeRepository treeRepository, IntentNodeRepository nodeRepository) {
        this.treeRepository = treeRepository;
        this.nodeRepository = nodeRepository;
    }

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

    @Transactional(readOnly = true)
    public List<IntentTreeSnapshot> activeTrees() {
        return treeRepository.findByStatusOrderByUpdatedAtDesc(STATUS_ACTIVE).stream()
                .map(tree -> new IntentTreeSnapshot(tree,
                        nodeRepository.findByTreeCodeAndVersionOrderBySortOrderAscIdAsc(tree.treeCode, tree.version)))
                .toList();
    }

    @Transactional(readOnly = true)
    public IntentTreeSnapshot getVersion(String treeCode, int version) {
        IntentTreeEntity tree = treeRepository.findByTreeCodeAndVersion(treeCode, version)
                .orElseThrow(() -> new BusinessException("INTENT_TREE_NOT_FOUND",
                        "Intent tree not found: %s@%d".formatted(treeCode, version)));
        return new IntentTreeSnapshot(tree,
                nodeRepository.findByTreeCodeAndVersionOrderBySortOrderAscIdAsc(treeCode, version));
    }

    @Transactional(readOnly = true)
    public List<IntentLeafView> activeLeaves() {
        List<IntentLeafView> leaves = new ArrayList<>();
        for (IntentTreeEntity tree : treeRepository.findByStatusOrderByUpdatedAtDesc(STATUS_ACTIVE)) {
            List<IntentNodeEntity> nodes = nodeRepository.findByTreeCodeAndVersionOrderBySortOrderAscIdAsc(
                    tree.treeCode, tree.version);
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
                readList(node.requiredEvidenceTypesJson)
        );
    }

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
        entity.enabled = request.enabled() == null || request.enabled();
        entity.sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
    }

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

    private int nextVersion(String treeCode) {
        return treeRepository.findByTreeCodeOrderByVersionDesc(treeCode).stream()
                .map(tree -> tree.version == null ? 0 : tree.version)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

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

    private String normalizeNodeType(String nodeType) {
        String normalized = nodeType == null ? "" : nodeType.trim().toUpperCase();
        if (!List.of(NODE_ROOT, NODE_CATEGORY, NODE_LEAF).contains(normalized)) {
            throw new BusinessException("INTENT_NODE_TYPE_INVALID", "Unsupported intent node type: " + nodeType);
        }
        return normalized;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
