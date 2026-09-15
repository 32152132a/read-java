package com.readenglish.learningcontent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class LearningStageModels {

  private LearningStageModels() {}

  public record LearningUnitResponse(
      String id, String title, String contentType, JsonNode content) {}

  public record LearningSessionResponse(
      String sessionId,
      String flowNodeId,
      String templateCode,
      boolean reviewMode,
      int currentUnitIndex,
      int total,
      List<LearningUnitResponse> units) {}

  public record UpdatePositionRequest(
      @NotBlank(message = "学习单元不能为空") String unitId,
      @PositiveOrZero(message = "学习位置不能小于 0") int unitIndex) {}

  public record PositionResponse(String sessionId, String unitId, int unitIndex) {}
}
