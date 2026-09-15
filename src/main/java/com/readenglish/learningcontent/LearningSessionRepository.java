package com.readenglish.learningcontent;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface LearningSessionRepository extends JpaRepository<LearningSessionEntity, String> {

  Optional<LearningSessionEntity>
      findFirstByUserIdAndFlowNodeIdAndTemplateCodeAndReviewModeOrderByIdDesc(
          String userId, String flowNodeId, String templateCode, boolean reviewMode);
}
