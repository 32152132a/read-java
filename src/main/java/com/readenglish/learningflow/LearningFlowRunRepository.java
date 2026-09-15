package com.readenglish.learningflow;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LearningFlowRunRepository extends JpaRepository<LearningFlowRunEntity, String> {

  Optional<LearningFlowRunEntity> findByUserIdAndActiveTrue(String userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select run from LearningFlowRunEntity run where run.userId = :userId and run.active = true")
  Optional<LearningFlowRunEntity> findActiveForUpdate(@Param("userId") String userId);
}
