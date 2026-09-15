package com.readenglish.learningflow;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LearningFlowConfigRepository extends JpaRepository<LearningFlowConfigEntity, String> {

  Optional<LearningFlowConfigEntity> findByUserIdAndActiveTrue(String userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select config from LearningFlowConfigEntity config where config.userId = :userId and config.active = true")
  Optional<LearningFlowConfigEntity> findActiveForUpdate(@Param("userId") String userId);
}
