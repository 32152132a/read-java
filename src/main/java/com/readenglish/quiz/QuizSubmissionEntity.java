package com.readenglish.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_submissions")
class QuizSubmissionEntity {

  @Id private String id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(name = "question_id", nullable = false)
  private String questionId;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(name = "selected_option_id", nullable = false)
  private String selectedOptionId;

  @Column(nullable = false)
  private boolean correct;

  protected QuizSubmissionEntity() {}

  QuizSubmissionEntity(
      String id,
      String userId,
      String questionId,
      String sessionId,
      String selectedOptionId,
      boolean correct) {
    this.id = id;
    this.userId = userId;
    this.questionId = questionId;
    this.sessionId = sessionId;
    this.selectedOptionId = selectedOptionId;
    this.correct = correct;
  }
}
