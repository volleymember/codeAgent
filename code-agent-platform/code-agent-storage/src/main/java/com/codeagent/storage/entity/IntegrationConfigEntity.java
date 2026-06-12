package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "integration_config")
public class IntegrationConfigEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String platform;

    @Column(name = "project_key")
    public String projectKey;

    @Column(name = "base_url", nullable = false)
    public String baseUrl;

    @Column(name = "auth_type", nullable = false)
    public String authType;

    @Column(name = "secret_ref", nullable = false)
    public String secretRef;

    @Column
    public Boolean enabled = true;

    @Column(name = "last_verified_at")
    public LocalDateTime lastVerifiedAt;

    @Column(name = "connection_status")
    public String connectionStatus;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
