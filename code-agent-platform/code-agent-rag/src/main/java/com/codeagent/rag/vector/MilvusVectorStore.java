package com.codeagent.rag.vector;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.config.EmbeddingProperties;
import com.codeagent.rag.config.MilvusProperties;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MilvusVectorStore implements VectorStore {
    public static final String ID_FIELD = "id";
    public static final String VECTOR_FIELD = "embedding";
    public static final String CHUNK_ID_FIELD = "chunk_id";
    public static final String EVIDENCE_ID_FIELD = "evidence_id";
    public static final String PROJECT_KEY_FIELD = "project_key";
    public static final String BRANCH_FIELD = "branch";
    public static final String COMMIT_ID_FIELD = "commit_id";
    public static final String BUILD_ID_FIELD = "build_id";
    public static final String EVIDENCE_TYPE_FIELD = "evidence_type";
    public static final String SOURCE_SYSTEM_FIELD = "source_system";
    public static final String SOURCE_URL_FIELD = "source_url";
    public static final String FILE_PATH_FIELD = "file_path";
    public static final String LINE_RANGE_FIELD = "line_range";

    private final MilvusProperties milvusProperties;
    private final EmbeddingProperties embeddingProperties;
    private final MilvusClientV2 client;
    private final Gson gson = new Gson();
    private final Map<String, Boolean> initializedCollections = new ConcurrentHashMap<>();

    public MilvusVectorStore(MilvusProperties milvusProperties, EmbeddingProperties embeddingProperties) {
        this.milvusProperties = milvusProperties;
        this.embeddingProperties = embeddingProperties;
        ConnectConfig.ConnectConfigBuilder<?, ?> builder = ConnectConfig.builder()
                .uri(milvusProperties.endpoint())
                .connectTimeoutMs(Math.max(1000, milvusProperties.getConnectTimeoutMs()))
                .rpcDeadlineMs(Math.max(1000, milvusProperties.getRpcDeadlineMs()));
        if (milvusProperties.getToken() != null && !milvusProperties.getToken().isBlank()) {
            builder.token(milvusProperties.getToken());
        }
        this.client = new MilvusClientV2(builder.build());
    }

    @Override
    public void upsert(String collection, String id, List<Double> vector, Map<String, Object> metadata) {
        validateVector(id, vector);
        String collectionName = collectionName(collection);
        ensureCollection(collectionName);
        try {
            client.upsert(UpsertReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(row(id, vector, metadata)))
                    .build());
        } catch (Exception e) {
            log.error("Milvus upsert failed collection={} id={}", collectionName, id, e);
            throw new BusinessException("MILVUS_UPSERT_FAILED", "Failed to upsert vector to Milvus.", e);
        }
    }

    @Override
    public List<VectorSearchHit> search(String collection, List<Double> queryVector, int topK, Map<String, Object> filter) {
        validateVector("query", queryVector);
        String collectionName = collectionName(collection);
        ensureCollection(collectionName);
        try {
            SearchResp response = client.search(SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField(VECTOR_FIELD)
                    .metricType(IndexParam.MetricType.COSINE)
                    .topK(Math.max(1, topK))
                    .filter(filterExpression(filter))
                    .outputFields(List.of(CHUNK_ID_FIELD))
                    .data(List.of(new FloatVec(toFloatList(queryVector))))
                    .searchParams(Map.of("ef", 64))
                    .build());
            return hits(response);
        } catch (Exception e) {
            log.error("Milvus search failed collection={} topK={} filter={}", collectionName, topK, filter, e);
            throw new BusinessException("MILVUS_SEARCH_FAILED", "Failed to search vectors from Milvus.", e);
        }
    }

    @Override
    public void delete(String collection, String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("VECTOR_ID_EMPTY", "Vector id must not be empty.");
        }
        String collectionName = collectionName(collection);
        ensureCollection(collectionName);
        try {
            client.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .ids(List.of(id))
                    .build());
        } catch (Exception e) {
            log.error("Milvus delete failed collection={} id={}", collectionName, id, e);
            throw new BusinessException("MILVUS_DELETE_FAILED", "Failed to delete vector from Milvus.", e);
        }
    }

    @PreDestroy
    public void close() {
        client.close();
    }

    private void ensureCollection(String collectionName) {
        if (Boolean.TRUE.equals(initializedCollections.get(collectionName))) {
            return;
        }
        synchronized (initializedCollections) {
            if (Boolean.TRUE.equals(initializedCollections.get(collectionName))) {
                return;
            }
            try {
                Boolean exists = client.hasCollection(HasCollectionReq.builder().collectionName(collectionName).build());
                if (!Boolean.TRUE.equals(exists)) {
                    createCollection(collectionName);
                }
                client.loadCollection(LoadCollectionReq.builder()
                        .collectionName(collectionName)
                        .sync(false)
                        .build());
                initializedCollections.put(collectionName, true);
            } catch (Exception e) {
                log.error("Milvus collection initialization failed collection={}", collectionName, e);
                throw new BusinessException("MILVUS_COLLECTION_INIT_FAILED",
                        "Failed to initialize Milvus collection: " + collectionName, e);
            }
        }
    }

    private void createCollection(String collectionName) {
        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(field(ID_FIELD, DataType.VarChar, 128, true));
        schema.addField(field(CHUNK_ID_FIELD, DataType.VarChar, 128, false));
        schema.addField(AddFieldReq.builder()
                .fieldName(VECTOR_FIELD)
                .dataType(DataType.FloatVector)
                .dimension(embeddingProperties.getDimensions())
                .build());
        IndexParam indexParam = IndexParam.builder()
                .fieldName(VECTOR_FIELD)
                .indexName("idx_" + VECTOR_FIELD)
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("M", 16, "efConstruction", 128))
                .build();
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(List.of(indexParam))
                .build());
        log.info("Created Milvus collection={} dimension={}", collectionName, embeddingProperties.getDimensions());
    }

    private AddFieldReq field(String name, DataType dataType, int maxLength, boolean primary) {
        return AddFieldReq.builder()
                .fieldName(name)
                .dataType(dataType)
                .maxLength(maxLength)
                .isPrimaryKey(primary)
                .autoID(false)
                .isNullable(!primary)
                .build();
    }

    private JsonObject row(String id, List<Double> vector, Map<String, Object> metadata) {
        JsonObject object = new JsonObject();
        object.addProperty(ID_FIELD, truncate(id, 128));
        object.add(CHUNK_ID_FIELD, gson.toJsonTree(truncate(stringValue(metadata, CHUNK_ID_FIELD), 128)));
        JsonArray vectorArray = new JsonArray();
        for (Double value : vector) {
            vectorArray.add(value.floatValue());
        }
        object.add(VECTOR_FIELD, vectorArray);
        return object;
    }

    private List<VectorSearchHit> hits(SearchResp response) {
        List<VectorSearchHit> hits = new ArrayList<>();
        if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return hits;
        }
        for (SearchResp.SearchResult result : response.getSearchResults().get(0)) {
            Map<String, Object> metadata = result.getEntity() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(result.getEntity());
            metadata.put(ID_FIELD, String.valueOf(result.getId()));
            hits.add(new VectorSearchHit(String.valueOf(result.getId()),
                    result.getScore() == null ? 0.0 : result.getScore(), metadata));
        }
        return hits;
    }

    private String filterExpression(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return "";
        }
        List<String> expressions = new ArrayList<>();
        Object chunkIds = filter.get(CHUNK_ID_FIELD);
        if (chunkIds instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object value : iterable) {
                if (value != null && !String.valueOf(value).isBlank()) {
                    values.add("\"" + escape(String.valueOf(value)) + "\"");
                }
            }
            if (!values.isEmpty()) {
                expressions.add(CHUNK_ID_FIELD + " in [" + String.join(",", values) + "]");
            }
        } else {
            addEquals(expressions, CHUNK_ID_FIELD, chunkIds);
        }
        return String.join(" and ", expressions);
    }

    private void addEquals(List<String> expressions, String field, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            expressions.add(field + " == \"" + escape(String.valueOf(value)) + "\"");
        }
    }

    private void validateVector(String id, List<Double> vector) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("VECTOR_ID_EMPTY", "Vector id must not be empty.");
        }
        if (vector == null || vector.isEmpty()) {
            throw new BusinessException("VECTOR_EMPTY", "Vector must not be empty.");
        }
        if (vector.size() != embeddingProperties.getDimensions()) {
            throw new BusinessException("VECTOR_DIMENSION_MISMATCH",
                    "Vector dimension %d does not match configured dimension %d."
                            .formatted(vector.size(), embeddingProperties.getDimensions()));
        }
    }

    private List<Float> toFloatList(List<Double> vector) {
        return vector.stream().map(Double::floatValue).toList();
    }

    private String collectionName(String collection) {
        return collection == null || collection.isBlank() ? milvusProperties.getCollection() : collection;
    }

    private String stringValue(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) {
            return "";
        }
        return String.valueOf(metadata.get(key));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
