package com.readenglish.quiz;

import jakarta.validation.constraints.NotBlank;

public final class QuizModels {

  private QuizModels() {}

  public record SubmitAnswerRequest(
      @NotBlank(message = "请选择答案") String selectedOptionId,
      @NotBlank(message = "学习会话不能为空") String sessionId) {}

  public record AnswerResponse(
      boolean correct, String selectedOptionId, String correctOptionId, String explanation) {}
}
