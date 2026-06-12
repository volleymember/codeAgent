package com.codeagent.rag.service;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceChunk;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ContentHashService {
    public String hashEvidence(Evidence evidence) {
        if (evidence == null) {
            throw new BusinessException("EVIDENCE_EMPTY", "Evidence must not be null when calculating content hash.");
        }
        return sha256(evidence.getContent());
    }

    public String hashChunk(EvidenceChunk chunk) {
        if (chunk == null) {
            throw new BusinessException("EVIDENCE_CHUNK_EMPTY", "Evidence chunk must not be null when calculating content hash.");
        }
        return sha256(chunk.getContent());
    }

    public String sha256(String content) {
        if (content == null) {
            throw new BusinessException("CONTENT_EMPTY", "Content must not be null when calculating SHA256 hash.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("SHA256_UNAVAILABLE", "SHA-256 digest algorithm is unavailable.", e);
        }
    }
}
