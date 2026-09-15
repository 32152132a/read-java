package com.readenglish.learningflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "learning_flow_nodes")
class LearningFlowNodeEntity {

  @Id private String id;

  @Column(name = "config_id", nullable = false)
  private String configId;

  @Column(name = "template_code", nullable = false)
  private String templateCode;

  @Column(name = "position_index", nullable = false)
  private int position;

  protected LearningFlowNodeEntity() {}

  LearningFlowNodeEntity(String id, String configId, String templateCode, int position) {
    this.id = id;
    this.configId = configId;
    this.templateCode = templateCode;
    this.position = position;
  }

  String getId() {
    return id;
  }

  String getTemplateCode() {
    return templateCode;
  }

  int getPosition() {
    return position;
  }
}
