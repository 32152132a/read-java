package com.readenglish.learningcontent;

import com.readenglish.common.api.ApiException;
import com.readenglish.learningcontent.LearningStageModels.LearningSessionResponse;
import com.readenglish.learningcontent.LearningStageModels.LearningUnitResponse;
import com.readenglish.learningcontent.LearningStageModels.PositionResponse;
import com.readenglish.learningcontent.LearningStageModels.UpdatePositionRequest;
import com.readenglish.learningflow.LearningFlowModels.CurrentFlow;
import com.readenglish.learningflow.LearningFlowModels.FlowNodeResponse;
import com.readenglish.learningflow.LearningFlowService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class LearningStageService {

  private final LearningFlowService learningFlowService;
  private final LearningUnitRepository unitRepository;
  private final LearningSessionRepository sessionRepository;
  private final ObjectMapper objectMapper;
  private final com.readenglish.content.ContentProjection projection;

  public LearningStageService(
      LearningFlowService learningFlowService,
      LearningUnitRepository unitRepository,
      LearningSessionRepository sessionRepository,
      ObjectMapper objectMapper,
      com.readenglish.content.ContentProjection projection) {
    this.projection = projection;
    this.learningFlowService = learningFlowService;
    this.unitRepository = unitRepository;
    this.sessionRepository = sessionRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public LearningSessionResponse getSession(
      String userId, String templateCode, String flowNodeId, boolean review) {
    if (flowNodeId == null || flowNodeId.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "正式学习或回顾必须提供流程节点");
    }

    CurrentFlow flow = learningFlowService.getCurrentFlow(userId);
    FlowNodeResponse flowNode =
        flow.nodes().stream()
            .filter(node -> node.id().equals(flowNodeId))
            .findFirst()
            .orElseThrow(() -> notFound("流程节点不存在"));
    if (!flowNode.templateCode().equals(templateCode)) {
      throw new ApiException(HttpStatus.CONFLICT, "FLOW_NODE_MISMATCH", "流程节点与学习功能不匹配");
    }
    if (review && !flowNode.reviewable()) {
      throw new ApiException(HttpStatus.CONFLICT, "FLOW_NODE_MISMATCH", "当前节点尚不能回顾");
    }
    if (!review
        && (flow.currentPosition() >= flow.nodes().size()
            || !flow.nodes().get(flow.currentPosition()).id().equals(flowNodeId))) {
      throw new ApiException(HttpStatus.CONFLICT, "FLOW_NODE_MISMATCH", "当前流程尚未进行到该节点");
    }

    List<LearningUnitEntity> units =
        unitRepository.findByTemplateCodeAndEnabledTrueOrderBySortOrderAsc(templateCode);
    if (units.isEmpty()) {
      throw notFound("当前学习阶段暂无内容");
    }

    String runNodeId = flow.runNodeIds().get(flowNode.position());
    LearningSessionEntity session =
        sessionRepository
            .findFirstByUserIdAndFlowNodeIdAndTemplateCodeAndReviewModeOrderByIdDesc(
                userId, flowNodeId, templateCode, review)
            .orElseGet(
                () ->
                    sessionRepository.save(
                        new LearningSessionEntity(
                            newId(), userId, runNodeId, flowNodeId, templateCode, review)));
    sessionRepository.flush();
    return toResponse(session, flowNodeId, units);
  }

  @Transactional
  public PositionResponse updatePosition(
      String userId, String sessionId, UpdatePositionRequest request) {
    LearningSessionEntity session =
        sessionRepository
            .findById(sessionId)
            .filter(item -> item.getUserId().equals(userId))
            .orElseThrow(() -> notFound("学习会话不存在"));
    List<LearningUnitEntity> units =
        unitRepository.findByTemplateCodeAndEnabledTrueOrderBySortOrderAsc(
            session.getTemplateCode());
    if (request.unitIndex() >= units.size()
        || !units.get(request.unitIndex()).getId().equals(request.unitId())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "学习单元与位置不匹配");
    }
    session.moveTo(request.unitIndex());
    return new PositionResponse(session.getId(), request.unitId(), request.unitIndex());
  }

  @Transactional(readOnly = true)
  public String getOwnedSessionTemplate(String userId, String sessionId) {
    return sessionRepository
        .findById(sessionId)
        .filter(session -> session.getUserId().equals(userId))
        .map(LearningSessionEntity::getTemplateCode)
        .orElseThrow(() -> notFound("学习会话不存在"));
  }

  @Transactional(readOnly = true)
  public int countUnits(String templateCode) {
    return unitRepository.findByTemplateCodeAndEnabledTrueOrderBySortOrderAsc(templateCode).size();
  }

  @Transactional(readOnly = true)
  public int getCurrentUnitIndex(String userId, String flowNodeId, String templateCode) {
    return sessionRepository
        .findFirstByUserIdAndFlowNodeIdAndTemplateCodeAndReviewModeOrderByIdDesc(
            userId, flowNodeId, templateCode, false)
        .map(LearningSessionEntity::getCurrentUnitIndex)
        .orElse(0);
  }

  private LearningSessionResponse toResponse(
      LearningSessionEntity session, String flowNodeId, List<LearningUnitEntity> units) {
    List<LearningUnitResponse> unitResponses =
        units.stream()
            .map(
                unit ->
                    new LearningUnitResponse(
                        unit.getId(),
                        unit.getTitle(),
                        unit.getContentType(),
                        readContent(unit.getContentJson())))
            .toList();
    var snapshot =
        projection.snapshot(
            session.getId(), session.getUserId(), objectMapper.valueToTree(unitResponses));
    unitResponses =
        java.util.stream.StreamSupport.stream(snapshot.spliterator(), false)
            .map(item -> objectMapper.treeToValue(item, LearningUnitResponse.class))
            .toList();
    return new LearningSessionResponse(
        session.getId(),
        flowNodeId,
        session.getTemplateCode(),
        session.isReviewMode(),
        session.getCurrentUnitIndex(),
        unitResponses.size(),
        unitResponses);
  }

  private JsonNode readContent(String contentJson) {
    try {
      return objectMapper.readTree(contentJson);
    } catch (JacksonException exception) {
      throw new IllegalStateException("课程内容格式不正确", exception);
    }
  }

  private static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
  }

  private static String newId() {
    return "session_" + UUID.randomUUID().toString().replace("-", "");
  }
}
