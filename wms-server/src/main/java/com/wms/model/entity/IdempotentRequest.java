package com.wms.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "idempotent_requests", uniqueConstraints =
        @UniqueConstraint(name = "uk_idempotent_request", columnNames = {"username", "scope", "request_key"}))
public class IdempotentRequest extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 50) private String username;
    @Column(nullable = false, length = 160) private String scope;
    @Column(name = "request_key", nullable = false, length = 128) private String requestKey;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "response_json", columnDefinition = "longtext") private String responseJson;

    protected IdempotentRequest() {}
    public IdempotentRequest(String username, String scope, String requestKey, String requestHash) {
        this.username = username; this.scope = scope; this.requestKey = requestKey; this.requestHash = requestHash;
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getScope() { return scope; }
    public String getRequestKey() { return requestKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
}
