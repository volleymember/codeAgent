package com.codeagent.core.intent;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.core.intent.dto.IntentNodeRequest;
import com.codeagent.core.intent.dto.IntentTreeCreateRequest;
import com.codeagent.storage.entity.IntentNodeEntity;
import com.codeagent.storage.entity.IntentTreeEntity;
import com.codeagent.storage.repository.IntentNodeRepository;
import com.codeagent.storage.repository.IntentTreeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentTreeServiceTest {
    private final IntentTreeRepository treeRepository = mock(IntentTreeRepository.class);
    private final IntentNodeRepository nodeRepository = mock(IntentNodeRepository.class);
    private final IntentTreeService service = new IntentTreeService(treeRepository, nodeRepository);

    @Test
    void createsIntentTreeAndNode() {
        when(treeRepository.findByTreeCodeOrderByVersionDesc("default")).thenReturn(List.of());
        when(treeRepository.findByTreeCodeAndVersion("default", 1)).thenReturn(Optional.empty(), Optional.of(tree("default", 1, "DRAFT")));
        when(treeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepository.findByTreeCodeAndVersionAndNodeCode("default", 1, "CI_FAILURE_ANALYSIS"))
                .thenReturn(Optional.empty());
        when(nodeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IntentTreeEntity tree = service.createTree(new IntentTreeCreateRequest("default", "Default", null));
        IntentNodeEntity node = service.createNode("default", new IntentNodeRequest(1, "CI_FAILURE_ANALYSIS",
                null, "CI failure", "LEAF", "Analyze CI failures", List.of("ci"),
                List.of("build failed"), 24, List.of("jenkins"), List.of("jenkins_log"), true, 1));

        assertThat(tree.version).isEqualTo(1);
        assertThat(tree.status).isEqualTo("DRAFT");
        assertThat(node.nodeCode).isEqualTo("CI_FAILURE_ANALYSIS");
        assertThat(node.nodeType).isEqualTo("LEAF");
        assertThat(node.keywordsJson).contains("ci");
    }

    @Test
    void rejectsActivationWithoutEnabledLeaf() {
        when(treeRepository.findByTreeCodeAndVersion("default", 2)).thenReturn(Optional.of(tree("default", 2, "DRAFT")));
        when(nodeRepository.existsByTreeCodeAndVersionAndNodeTypeAndEnabledTrue("default", 2, "LEAF")).thenReturn(false);

        assertThatThrownBy(() -> service.activate("default", 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot activate");
    }

    @Test
    void keepsOnlyOneActiveVersionPerTreeCode() {
        IntentTreeEntity previous = tree("default", 1, "ACTIVE");
        IntentTreeEntity next = tree("default", 2, "DRAFT");
        previous.id = 1L;
        next.id = 2L;
        when(treeRepository.findByTreeCodeAndVersion("default", 2)).thenReturn(Optional.of(next));
        when(nodeRepository.existsByTreeCodeAndVersionAndNodeTypeAndEnabledTrue("default", 2, "LEAF")).thenReturn(true);
        when(treeRepository.findByTreeCodeAndStatus("default", "ACTIVE")).thenReturn(List.of(previous));
        when(treeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IntentTreeEntity activated = service.activate("default", 2);

        assertThat(activated.status).isEqualTo("ACTIVE");
        assertThat(previous.status).isEqualTo("DISABLED");
        verify(treeRepository).save(previous);
        verify(treeRepository).save(next);
    }

    @Test
    void activeLeavesExcludeDisabledNodes() {
        IntentTreeEntity active = tree("default", 1, "ACTIVE");
        IntentNodeEntity root = node("ROOT", null, "ROOT", true);
        IntentNodeEntity enabled = node("CI_FAILURE_ANALYSIS", "ROOT", "LEAF", true);
        IntentNodeEntity disabled = node("PROD_INCIDENT_ANALYSIS", "ROOT", "LEAF", false);
        when(treeRepository.findByStatusOrderByUpdatedAtDesc("ACTIVE")).thenReturn(List.of(active));
        when(nodeRepository.findByTreeCodeAndVersionOrderBySortOrderAscIdAsc("default", 1))
                .thenReturn(List.of(root, enabled, disabled));

        assertThat(service.activeLeaves())
                .hasSize(1)
                .first()
                .extracting("nodeCode")
                .isEqualTo("CI_FAILURE_ANALYSIS");
    }

    private IntentTreeEntity tree(String code, int version, String status) {
        IntentTreeEntity entity = new IntentTreeEntity();
        entity.treeCode = code;
        entity.treeName = code;
        entity.version = version;
        entity.status = status;
        entity.updatedAt = LocalDateTime.now();
        return entity;
    }

    private IntentNodeEntity node(String code, String parent, String type, boolean enabled) {
        IntentNodeEntity entity = new IntentNodeEntity();
        entity.treeCode = "default";
        entity.version = 1;
        entity.nodeCode = code;
        entity.parentCode = parent;
        entity.nodeName = code;
        entity.nodeType = type;
        entity.enabled = enabled;
        entity.sortOrder = 0;
        return entity;
    }
}
