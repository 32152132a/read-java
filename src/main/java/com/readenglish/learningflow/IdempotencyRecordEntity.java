package com.readenglish.learningflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
class IdempotencyRecordEntity {

  @Id private String id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(nullable = false)
  private String scope;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "response_json", nullable = false)
  private String responseJson;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected IdempotencyRecordEntity() {}

  IdempotencyRecordEntity(
      String id,
      String userId,
      String scope,
      String idempotencyKey,
      String responseJson,
      Instant expiresAt) {
    this.id = id;
    this.userId = userId;
    this.scope = scope;
    this.idempotencyKey = idempotencyKey;
    this.responseJson = responseJson;
    this.expiresAt = expiresAt;
  }

  String getResponseJson() {
    return responseJson;
  }
}
