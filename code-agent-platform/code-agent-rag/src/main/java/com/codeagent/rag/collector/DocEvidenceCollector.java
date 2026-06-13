package com.codeagent.rag.collector;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceType;
import com.codeagent.rag.model.IndexEvidenceRequest;
import com.codeagent.rag.model.SourceSystem;
import com.codeagent.storage.entity.DocumentEntity;
import com.codeagent.storage.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文档类 Evidence 采集器。
 *
 * <p>该采集器负责将文档、Markdown、Java 代码等输入请求转换为统一的 Evidence 对象，
 * 供后续 RAG 索引流程进行分块、向量化和检索使用。</p>
 *
 * <p>采集过程中会尝试根据 docId 查询已存储的 DocumentEntity，
 * 并使用请求参数优先、文档存储信息兜底的方式补全 sourceUrl、filePath、title、rawRef 等字段。</p>
 */
@Slf4j
@Component
public class DocEvidenceCollector extends CollectorSupport implements EvidenceCollector {

    /**
     * 文档仓储，用于根据 docId 查询已有文档元信息。
     */
    private final DocumentRepository documentRepository;

    /**
     * 创建文档 Evidence 采集器。
     *
     * @param documentRepository 文档仓储
     */
    public DocEvidenceCollector(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * 返回当前采集器对应的来源系统。
     *
     * @return 文档来源系统 DOC
     */
    @Override
    public SourceSystem sourceSystem() {
        return SourceSystem.DOC;
    }

    /**
     * 根据索引请求采集并构建 Evidence。
     *
     * <p>该方法主要完成以下工作：</p>
     * <ol>
     *     <li>校验 Evidence 类型是否属于文档类支持范围</li>
     *     <li>根据 docId 查询文档元信息</li>
     *     <li>校验 content 是否存在</li>
     *     <li>构建 metadata</li>
     *     <li>组合请求参数和文档信息，生成最终 Evidence</li>
     * </ol>
     *
     * @param request Evidence 索引请求
     * @return 构建完成的 Evidence
     * @throws BusinessException 当 Evidence 类型不支持或内容为空时抛出
     */
    @Override
    public Evidence collect(IndexEvidenceRequest request) {
        // 仅支持文档、Markdown 和 Java 代码这几类文档型 Evidence
        if (request.getEvidenceType() != EvidenceType.DOCUMENT
                && request.getEvidenceType() != EvidenceType.MARKDOWN
                && request.getEvidenceType() != EvidenceType.JAVA_CODE) {
            throw new BusinessException("DOC_EVIDENCE_TYPE_UNSUPPORTED",
                    "Unsupported document evidence type: " + request.getEvidenceType());
        }

        // 如果请求中携带 docId，则尝试加载已存储的文档元信息
        DocumentEntity document = resolveDocument(request.getDocId());

        // 文档内容必须由请求传入；原始文件路径或引用信息只作为 metadata/source 信息保留
        String content = fallback(request.getContent(), "");
        requireText(content, "DOCUMENT_CONTENT_REQUIRED",
                "content is required for document evidence indexing; original file metadata can be carried by rawRef/filePath.");

        // 先构建通用 metadata，再追加文档采集器特有字段
        Map<String, Object> metadata = metadata(request);
        put(metadata, "docId", request.getDocId());
        put(metadata, "rawRef", fallback(request.getRawRef(), document == null ? null : document.rawRef));

        log.info("Collected document evidence type={} projectKey={} docId={}",
                request.getEvidenceType(), request.getProjectKey(), request.getDocId());

        return Evidence.builder()
                .evidenceId(nextEvidenceId())
                .taskNo(request.getTaskNo())
                .projectKey(request.getProjectKey())
                .branch(request.getBranch())
                .commitId(request.getCommitId())
                .buildId(request.getBuildId())
                .evidenceType(request.getEvidenceType())
                .sourceSystem(SourceSystem.DOC)
                .sourceUrl(sourceUrl(request, document))
                .filePath(filePath(request, document))
                .symbolName(request.getSymbolName())
                .title(title(request, document))
                .summary(request.getSummary())
                .keywords(fallbackKeywords(request.getKeywords(), request.getEvidenceType(), request.getProjectKey()))
                .content(content)
                .rawRef(fallback(request.getRawRef(), document == null ? null : document.rawRef))
                .metadata(metadata)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 根据 docId 查询文档实体。
     *
     * <p>当 docId 为空时直接返回 null；当仓储中不存在对应文档时也返回 null，
     * 后续字段会使用请求参数或默认值兜底。</p>
     *
     * @param docId 文档 ID
     * @return 查询到的 DocumentEntity；不存在时返回 null
     */
    private DocumentEntity resolveDocument(String docId) {
        if (docId == null || docId.isBlank()) {
            return null;
        }
        return documentRepository.findByDocId(docId).orElse(null);
    }

    /**
     * 解析 Evidence 的来源 URL。
     *
     * <p>优先级如下：</p>
     * <ol>
     *     <li>请求中的 sourceUrl</li>
     *     <li>文档实体中的 sourceUri</li>
     *     <li>请求中的 rawRef</li>
     *     <li>根据 docId 或 projectKey 生成的 doc:// 地址</li>
     * </ol>
     *
     * @param request  Evidence 索引请求
     * @param document 已查询到的文档实体，可为空
     * @return Evidence 来源 URL
     */
    private String sourceUrl(IndexEvidenceRequest request, DocumentEntity document) {
        if (request.getSourceUrl() != null && !request.getSourceUrl().isBlank()) {
            return request.getSourceUrl();
        }
        if (document != null && document.sourceUri != null && !document.sourceUri.isBlank()) {
            return document.sourceUri;
        }
        if (request.getRawRef() != null && !request.getRawRef().isBlank()) {
            return request.getRawRef();
        }
        return "doc://" + fallback(request.getDocId(), request.getProjectKey());
    }

    /**
     * 解析 Evidence 的文件路径。
     *
     * <p>优先级如下：</p>
     * <ol>
     *     <li>请求中的 filePath</li>
     *     <li>文档实体中的 rawRef</li>
     *     <li>请求中的 docId</li>
     *     <li>请求中的 projectKey</li>
     * </ol>
     *
     * @param request  Evidence 索引请求
     * @param document 已查询到的文档实体，可为空
     * @return Evidence 文件路径
     */
    private String filePath(IndexEvidenceRequest request, DocumentEntity document) {
        if (request.getFilePath() != null && !request.getFilePath().isBlank()) {
            return request.getFilePath();
        }
        if (document != null && document.rawRef != null && !document.rawRef.isBlank()) {
            return document.rawRef;
        }
        return fallback(request.getDocId(), request.getProjectKey());
    }

    /**
     * 解析 Evidence 标题。
     *
     * <p>优先级如下：</p>
     * <ol>
     *     <li>请求中的 title</li>
     *     <li>文档实体中的 title</li>
     *     <li>默认标题 {@code Document evidence}</li>
     * </ol>
     *
     * @param request  Evidence 索引请求
     * @param document 已查询到的文档实体，可为空
     * @return Evidence 标题
     */
    private String title(IndexEvidenceRequest request, DocumentEntity document) {
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            return request.getTitle();
        }
        if (document != null && document.title != null && !document.title.isBlank()) {
            return document.title;
        }
        return "Document evidence";
    }

    /**
     * 解析 Evidence 关键词。
     *
     * <p>如果请求中已经提供关键词，则直接使用；
     * 否则生成默认关键词，包括 document、Evidence 类型和项目标识。</p>
     *
     * @param keywords     请求中的关键词列表
     * @param evidenceType Evidence 类型
     * @param projectKey   项目标识
     * @return Evidence 关键词列表
     */
    private List<String> fallbackKeywords(List<String> keywords, EvidenceType evidenceType, String projectKey) {
        if (keywords != null && !keywords.isEmpty()) {
            return keywords;
        }
        return List.of("document", evidenceType.name().toLowerCase(), projectKey);
    }
}