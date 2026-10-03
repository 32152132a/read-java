package com.readenglish.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class EvaluationModels {
  private EvaluationModels() {}

  public record Create(
      @NotBlank @Size(max = 64) String wordId,
      @Size(max = 64) String sessionId,
      @NotBlank @Size(max = 230000) String audioBase64) {}

  public record Phoneme(
      String reference,
      String detected,
      Double accuracy,
      Boolean expectedStress,
      Boolean detectedStress) {}

  public record Score(
      Double overall, double accuracy, List<Phoneme> phonemes, String providerRequestId) {}

  public record Result(
      String word,
      String accent,
      Score scores,
      String summary,
      List<String> suggestions,
      String adviceSource) {}

  public record Response(String evaluationId, String status, Result result, String errorCode) {}
}
