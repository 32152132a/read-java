package com.readenglish.learningflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "learning_flow_runs")
class LearningFlowRunEntity {

  @Id private String id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(name = "config_id", nullable = false)
  private String configId;

  @Column(name = "config_version", nullable = false)
  private long configVersion;

  @Column(name = "current_position", nullable = false)
  private int currentPosition;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private boolean active;

  protected LearningFlowRunEntity() {}

  LearningFlowRunEntity(String id, String userId, String configId, long configVersion) {
    this.id = id;
    this.userId = userId;
    this.configId = configId;
    this.configVersion = configVersion;
    this.currentPosition = 0;
    this.status = "NOT_STARTED";
    this.active = true;
  }

  String getId() {
    return id;
  }

  int getCurrentPosition() {
    return currentPosition;
  }

  String getStatus() {
    return status;
  }

  void deactivate() {
    active = false;
  }

  void advance(boolean completed) {
    currentPosition++;
    status = completed ? "COMPLETED" : "IN_PROGRESS";
  }
}
