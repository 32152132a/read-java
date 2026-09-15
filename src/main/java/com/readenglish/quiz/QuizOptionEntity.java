package com.readenglish.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_options")
class QuizOptionEntity {

  @Id private String id;

  @Column(name = "question_id", nullable = false)
  private String questionId;

  @Column(name = "option_code", nullable = false)
  private String optionCode;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  private boolean correct;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected QuizOptionEntity() {}

  String getId() {
    return id;
  }

  String getOptionCode() {
    return optionCode;
  }

  boolean isCorrect() {
    return correct;
  }
}
