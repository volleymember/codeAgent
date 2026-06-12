package com.codeagent.storage.raw;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.storage.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class MinioRawOutputStore implements RawOutputStore {
    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioRawOutputStore(ObjectProvider<MinioClient> minioClient, MinioProperties properties) {
        this.minioClient = minioClient.getIfAvailable();
        this.properties = properties;
    }

    @Override
    public String saveJson(String taskNo, String objectName, Object payload) {
        if (minioClient == null || !properties.configured()) {
            throw new BusinessException("MINIO_NOT_CONFIGURED", "MinIO is required to persist raw tool output.");
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            }
            byte[] body = JsonSupport.toJson(payload).getBytes(StandardCharsets.UTF_8);
            String safeObjectName = objectName.replaceAll("[^A-Za-z0-9._-]", "-");
            String path = "tool-output/%s/%s/%s-%s.json".formatted(LocalDate.now(), taskNo, safeObjectName, UUID.randomUUID());
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(path)
                    .contentType("application/json")
                    .stream(new ByteArrayInputStream(body), body.length, -1)
                    .build());
            return "minio://%s/%s".formatted(properties.getBucket(), path);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("RAW_OUTPUT_STORE_FAILED", "Failed to persist raw output to MinIO.", e);
        }
    }
}
