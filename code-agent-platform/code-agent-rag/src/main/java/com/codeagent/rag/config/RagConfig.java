package com.codeagent.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({EmbeddingProperties.class, RagProperties.class, MilvusProperties.class})
public class RagConfig {
}
