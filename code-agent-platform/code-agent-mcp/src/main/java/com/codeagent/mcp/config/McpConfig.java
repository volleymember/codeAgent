package com.codeagent.mcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({IntegrationsProperties.class, DataSandboxProperties.class})
public class McpConfig {
}
