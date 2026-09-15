package com.readenglish.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_questions")
class QuizQuestionEntity {

  @Id private String id;

  @Column(name = "template_code", nullable = false)
  private String templateCode;

  @Column(name = "unit_id", nullable = false)
  private String unitId;

  @Column(nullable = false)
  private String prompt;

  @Column(nullable = false)
  private String explanation;

  protected QuizQuestionEntity() {}

  String getId() {
    return id;
  }

  String getTemplateCode() {
    return templateCode;
  }

  String getExplanation() {
    return explanation;
  }
}
