package com.readenglish.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity {

  @Id private String id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected RefreshTokenEntity() {}

  RefreshTokenEntity(String id, String userId, String tokenHash, Instant expiresAt) {
    this.id = id;
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  String getUserId() {
    return userId;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  Instant getRevokedAt() {
    return revokedAt;
  }

  void revoke() {
    revokedAt = Instant.now();
  }
}
