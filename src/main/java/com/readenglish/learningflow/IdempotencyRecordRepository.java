package com.readenglish.learningflow;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, String> {

  Optional<IdempotencyRecordEntity> findByUserIdAndScopeAndIdempotencyKeyAndExpiresAtAfter(
      String userId, String scope, String idempotencyKey, Instant now);
}
