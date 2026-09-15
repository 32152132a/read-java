package com.readenglish.learningcontent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "learning_units")
class LearningUnitEntity {

  @Id private String id;

  @Column(name = "template_code", nullable = false)
  private String templateCode;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(nullable = false)
  private String title;

  @Column(name = "content_json", nullable = false)
  private String contentJson;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean enabled;

  protected LearningUnitEntity() {}

  String getId() {
    return id;
  }

  String getTemplateCode() {
    return templateCode;
  }

  String getContentType() {
    return contentType;
  }

  String getTitle() {
    return title;
  }

  String getContentJson() {
    return contentJson;
  }
}
