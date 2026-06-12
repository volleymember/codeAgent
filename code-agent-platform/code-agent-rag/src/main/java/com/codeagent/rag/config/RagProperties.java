package com.codeagent.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private int chunkSize = 800;
    private int chunkOverlap = 100;
    private int vectorTopK = 20;
    private int bm25TopK = 20;
    private int finalEvidenceLimit = 8;

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int getVectorTopK() {
        return vectorTopK;
    }

    public void setVectorTopK(int vectorTopK) {
        this.vectorTopK = vectorTopK;
    }

    public int getBm25TopK() {
        return bm25TopK;
    }

    public void setBm25TopK(int bm25TopK) {
        this.bm25TopK = bm25TopK;
    }

    public int getFinalEvidenceLimit() {
        return finalEvidenceLimit;
    }

    public void setFinalEvidenceLimit(int finalEvidenceLimit) {
        this.finalEvidenceLimit = finalEvidenceLimit;
    }
}
