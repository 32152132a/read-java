package com.readenglish.learningflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "learning_flow_run_nodes")
class LearningFlowRunNodeEntity {

  @Id private String id;

  @Column(name = "run_id", nullable = false)
  private String runId;

  @Column(name = "source_node_id", nullable = false)
  private String sourceNodeId;

  @Column(name = "template_code", nullable = false)
  private String templateCode;

  @Column(name = "position_index", nullable = false)
  private int position;

  @Column(nullable = false)
  private String status;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected LearningFlowRunNodeEntity() {}

  LearningFlowRunNodeEntity(
      String id, String runId, String sourceNodeId, String templateCode, int position) {
    this.id = id;
    this.runId = runId;
    this.sourceNodeId = sourceNodeId;
    this.templateCode = templateCode;
    this.position = position;
    this.status = "NOT_STARTED";
  }

  String getId() {
    return id;
  }

  String getSourceNodeId() {
    return sourceNodeId;
  }

  String getTemplateCode() {
    return templateCode;
  }

  int getPosition() {
    return position;
  }

  String getStatus() {
    return status;
  }

  void complete() {
    status = "COMPLETED";
    completedAt = Instant.now();
  }
}
