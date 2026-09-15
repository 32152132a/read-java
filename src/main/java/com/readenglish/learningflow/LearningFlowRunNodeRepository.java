package com.readenglish.learningflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface LearningFlowRunNodeRepository extends JpaRepository<LearningFlowRunNodeEntity, String> {

  List<LearningFlowRunNodeEntity> findByRunIdOrderByPositionAsc(String runId);
}
