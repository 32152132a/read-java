package com.readenglish.learningflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface LearningFlowNodeRepository extends JpaRepository<LearningFlowNodeEntity, String> {

  List<LearningFlowNodeEntity> findByConfigIdOrderByPositionAsc(String configId);
}
