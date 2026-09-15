package com.readenglish.learningcontent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "learning_sessions")
class LearningSessionEntity {

  @Id private String id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(name = "run_node_id")
  private String runNodeId;

  @Column(name = "flow_node_id")
  private String flowNodeId;

  @Column(name = "template_code", nullable = false)
  private String templateCode;

  @Column(name = "review_mode", nullable = false)
  private boolean reviewMode;

  @Column(name = "current_unit_index", nullable = false)
  private int currentUnitIndex;

  protected LearningSessionEntity() {}

  LearningSessionEntity(
      String id,
      String userId,
      String runNodeId,
      String flowNodeId,
      String templateCode,
      boolean reviewMode) {
    this.id = id;
    this.userId = userId;
    this.runNodeId = runNodeId;
    this.flowNodeId = flowNodeId;
    this.templateCode = templateCode;
    this.reviewMode = reviewMode;
    this.currentUnitIndex = 0;
  }

  String getId() {
    return id;
  }

  String getUserId() {
    return userId;
  }

  String getTemplateCode() {
    return templateCode;
  }

  boolean isReviewMode() {
    return reviewMode;
  }

  int getCurrentUnitIndex() {
    return currentUnitIndex;
  }

  void moveTo(int unitIndex) {
    currentUnitIndex = unitIndex;
  }
}
