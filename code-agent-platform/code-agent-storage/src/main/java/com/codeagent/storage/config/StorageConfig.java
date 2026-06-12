package com.codeagent.storage.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import okhttp3.OkHttpClient;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class StorageConfig {
    @Bean
    @ConditionalOnProperty(prefix = "minio", name = {"endpoint", "access-key", "secret-key"})
    MinioClient minioClient(MinioProperties properties) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getConnectTimeoutMillis())))
                .readTimeout(Duration.ofMillis(Math.max(1000, properties.getReadTimeoutMillis())))
                .writeTimeout(Duration.ofMillis(Math.max(1000, properties.getWriteTimeoutMillis())))
                .build();
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .httpClient(httpClient)
                .build();
    }
}
