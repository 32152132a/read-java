package com.readenglish.learningflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public final class LearningFlowModels {

  private LearningFlowModels() {}

  public record TemplateResponse(
      String code,
      String name,
      String shortName,
      String route,
      String icon,
      boolean enabled,
      boolean repeatable) {}

  public record FlowNodeResponse(
      String id,
      String templateCode,
      String name,
      int position,
      String status,
      boolean reviewable,
      String route) {}

  public record FlowResponse(
      String id, long version, int currentNodeIndex, String status, List<FlowNodeResponse> nodes) {}

  public record SaveFlowRequest(
      @PositiveOrZero(message = "流程版本不能小于 0") long version,
      @NotEmpty(message = "学习流程至少保留一个节点") List<@Valid SaveFlowNodeRequest> nodes) {}

  public record SaveFlowNodeRequest(
      String clientNodeId, @NotBlank(message = "功能模板不能为空") String templateCode) {}

  public record CompleteFlowRequest(
      @NotBlank(message = "流程节点不能为空") String flowNodeId, String lastUnitId) {}

  public record NextNodeResponse(String id, String templateCode, String name, String route) {}

  public record CompleteFlowResponse(
      String completedNodeId,
      boolean flowCompleted,
      int currentNodeIndex,
      NextNodeResponse nextNode) {}

  public record CurrentFlow(
      String flowId,
      long version,
      String runId,
      int currentPosition,
      String status,
      List<FlowNodeResponse> nodes,
      List<String> runNodeIds) {}
}
