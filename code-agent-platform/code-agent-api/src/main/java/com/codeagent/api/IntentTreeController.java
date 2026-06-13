package com.codeagent.api;

import com.codeagent.common.api.ApiResponse;
import com.codeagent.core.intent.IntentTreeService;
import com.codeagent.core.intent.dto.IntentLeafView;
import com.codeagent.core.intent.dto.IntentNodeRequest;
import com.codeagent.core.intent.dto.IntentTreeCreateRequest;
import com.codeagent.core.intent.dto.IntentTreeSnapshot;
import com.codeagent.storage.entity.IntentNodeEntity;
import com.codeagent.storage.entity.IntentTreeEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/intent-trees")
public class IntentTreeController {
    private final IntentTreeService service;

    public IntentTreeController(IntentTreeService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<IntentTreeEntity> createTree(@Valid @RequestBody IntentTreeCreateRequest request) {
        return ApiResponse.success(service.createTree(request));
    }

    @PostMapping("/{treeCode}/nodes")
    public ApiResponse<IntentNodeEntity> createNode(@PathVariable String treeCode,
                                                    @Valid @RequestBody IntentNodeRequest request) {
        return ApiResponse.success(service.createNode(treeCode, request));
    }

    @PutMapping("/{treeCode}/nodes/{nodeCode}")
    public ApiResponse<IntentNodeEntity> updateNode(@PathVariable String treeCode,
                                                    @PathVariable String nodeCode,
                                                    @Valid @RequestBody IntentNodeRequest request) {
        return ApiResponse.success(service.updateNode(treeCode, nodeCode, request));
    }

    @PostMapping("/{treeCode}/versions/{version}/activate")
    public ApiResponse<IntentTreeEntity> activate(@PathVariable String treeCode, @PathVariable int version) {
        return ApiResponse.success(service.activate(treeCode, version));
    }

    @GetMapping("/active")
    public ApiResponse<List<IntentTreeSnapshot>> active() {
        return ApiResponse.success(service.activeTrees());
    }

    @GetMapping("/active/leaves")
    public ApiResponse<List<IntentLeafView>> activeLeaves() {
        return ApiResponse.success(service.activeLeaves());
    }

    @GetMapping("/{treeCode}/versions/{version}")
    public ApiResponse<IntentTreeSnapshot> version(@PathVariable String treeCode, @PathVariable int version) {
        return ApiResponse.success(service.getVersion(treeCode, version));
    }
}
