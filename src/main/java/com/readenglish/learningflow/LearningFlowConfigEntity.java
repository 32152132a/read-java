package com.readenglish.learningflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "learning_flow_configs")
class LearningFlowConfigEntity {

  @Id private String id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(nullable = false)
  private long version;

  @Column(nullable = false)
  private boolean active;

  protected LearningFlowConfigEntity() {}

  LearningFlowConfigEntity(String id, String userId, long version) {
    this.id = id;
    this.userId = userId;
    this.version = version;
    this.active = true;
  }

  String getId() {
    return id;
  }

  long getVersion() {
    return version;
  }

  void deactivate() {
    active = false;
  }
}
