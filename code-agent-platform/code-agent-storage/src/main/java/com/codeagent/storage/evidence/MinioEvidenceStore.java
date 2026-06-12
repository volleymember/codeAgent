package com.codeagent.storage.evidence;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.storage.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
public class MinioEvidenceStore {
    private static final String OBJECT_PREFIX = "evidence-raw";
    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioEvidenceStore(ObjectProvider<MinioClient> minioClient, MinioProperties properties) {
        this.minioClient = minioClient.getIfAvailable();
        this.properties = properties;
    }

    public StoredEvidenceObject uploadOriginalContent(String projectKey,
                                                       String evidenceId,
                                                       String fileName,
                                                       String content,
                                                       String contentHash) {
        ensureConfigured();
        if (content == null) {
            throw new BusinessException("EVIDENCE_CONTENT_EMPTY", "Original evidence content must not be null.");
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        validateSize(bytes.length);
        String objectName = objectName(projectKey, evidenceId, fileName, contentHash);
        return executeWithRetry("upload evidence object " + objectName, () -> {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .contentType("text/plain; charset=utf-8")
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .build());
            String objectId = "minio://%s/%s".formatted(properties.getBucket(), objectName);
            log.info("Uploaded evidence raw object evidenceId={} objectId={} fileSize={}", evidenceId, objectId, bytes.length);
            return new StoredEvidenceObject(objectId, bytes.length, contentHash);
        });
    }

    public String downloadOriginalContent(String objectId) {
        ensureConfigured();
        ParsedObject parsed = parseObjectId(objectId);
        return executeWithRetry("download evidence object " + objectId, () -> {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(parsed.bucket())
                    .object(parsed.objectName())
                    .build());
            validateSize(stat.size());
            try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(parsed.bucket())
                    .object(parsed.objectName())
                    .build())) {
                byte[] bytes = input.readAllBytes();
                validateSize(bytes.length);
                log.info("Downloaded evidence raw object objectId={} fileSize={}", objectId, bytes.length);
                return new String(bytes, StandardCharsets.UTF_8);
            }
        });
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
        }
    }

    private void validateSize(long size) {
        long max = Math.max(1, properties.getMaxEvidenceObjectBytes());
        if (size > max) {
            throw new BusinessException("EVIDENCE_OBJECT_TOO_LARGE",
                    "Evidence object size %d exceeds max allowed bytes %d.".formatted(size, max));
        }
    }

    private void ensureConfigured() {
        if (minioClient == null || !properties.configured()) {
            throw new BusinessException("MINIO_NOT_CONFIGURED", "MinIO is required to persist evidence raw content.");
        }
    }

    private String objectName(String projectKey, String evidenceId, String fileName, String contentHash) {
        String safeProject = safe(projectKey);
        String safeEvidence = safe(evidenceId);
        String safeFile = safe(fileName == null || fileName.isBlank() ? "raw.txt" : fileName);
        String hashPrefix = contentHash == null || contentHash.length() < 16 ? UUID.randomUUID().toString() : contentHash.substring(0, 16);
        return "%s/%s/%s/%s/%s-%s".formatted(
                OBJECT_PREFIX, LocalDate.now(), safeProject, safeEvidence, hashPrefix, safeFile);
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private ParsedObject parseObjectId(String objectId) {
        if (objectId == null || objectId.isBlank() || !objectId.startsWith("minio://")) {
            throw new BusinessException("MINIO_OBJECT_ID_INVALID", "Invalid MinIO objectId: " + objectId);
        }
        String withoutScheme = objectId.substring("minio://".length());
        int slash = withoutScheme.indexOf('/');
        if (slash <= 0 || slash == withoutScheme.length() - 1) {
            throw new BusinessException("MINIO_OBJECT_ID_INVALID", "Invalid MinIO objectId: " + objectId);
        }
        return new ParsedObject(withoutScheme.substring(0, slash), withoutScheme.substring(slash + 1));
    }

    private <T> T executeWithRetry(String operation, MinioOperation<T> operationCall) {
        int attempts = Math.max(1, properties.getRetryAttempts());
        Exception lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return operationCall.execute();
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                lastError = e;
                log.warn("MinIO operation failed operation={} attempt={}/{} error={}",
                        operation, attempt, attempts, e.toString());
                sleepBeforeRetry(attempt, attempts);
            }
        }
        throw new BusinessException("MINIO_EVIDENCE_STORE_FAILED",
                "MinIO operation failed after retries: " + operation, lastError);
    }

    private void sleepBeforeRetry(int attempt, int attempts) {
        if (attempt >= attempts) {
            return;
        }
        try {
            Thread.sleep(Math.max(0, properties.getRetryBackoffMillis()) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("MINIO_RETRY_INTERRUPTED", "MinIO retry interrupted.", e);
        }
    }

    @FunctionalInterface
    private interface MinioOperation<T> {
        T execute() throws Exception;
    }

    private record ParsedObject(String bucket, String objectName) {
    }
}
