package com.codeagent.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {
    private String host = "localhost";
    private int port = 19530;
    private String collection = "code_agent_chunks";
    private String uri;
    private String token;
    private long connectTimeoutMs = 10000;
    private long rpcDeadlineMs = 20000;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public long getRpcDeadlineMs() {
        return rpcDeadlineMs;
    }

    public void setRpcDeadlineMs(long rpcDeadlineMs) {
        this.rpcDeadlineMs = rpcDeadlineMs;
    }

    public String endpoint() {
        if (uri != null && !uri.isBlank()) {
            return uri;
        }
        return "http://%s:%d".formatted(host, port);
    }
}
