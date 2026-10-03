package com.readenglish.evaluation;

public interface OralEvaluationGateway {
  boolean available();

  EvaluationModels.Score evaluate(String word, byte[] wav);
}
