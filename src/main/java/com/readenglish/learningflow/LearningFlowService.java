package com.readenglish.learningflow;

import com.readenglish.common.api.ApiException;
import com.readenglish.learningflow.LearningFlowModels.CompleteFlowRequest;
import com.readenglish.learningflow.LearningFlowModels.CompleteFlowResponse;
import com.readenglish.learningflow.LearningFlowModels.CurrentFlow;
import com.readenglish.learningflow.LearningFlowModels.FlowNodeResponse;
import com.readenglish.learningflow.LearningFlowModels.FlowResponse;
import com.readenglish.learningflow.LearningFlowModels.NextNodeResponse;
import com.readenglish.learningflow.LearningFlowModels.SaveFlowRequest;
import com.readenglish.learningflow.LearningFlowModels.TemplateResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class LearningFlowService {

  private static final String COMPLETE_SCOPE = "learning-flow/current/complete";
  private static final List<String> DEFAULT_FLOW =
      List.of(
          "phoneme",
          "phoneme-compare",
          "phoneme-quiz",
          "syllable",
          "ipa-decoding",
          "word-decoding",
          "evaluation");

  private final FeatureTemplateRepository templateRepository;
  private final LearningFlowConfigRepository configRepository;
  private final LearningFlowNodeRepository nodeRepository;
  private final LearningFlowRunRepository runRepository;
  private final LearningFlowRunNodeRepository runNodeRepository;
  private final IdempotencyRecordRepository idempotencyRepository;
  private final ObjectMapper objectMapper;

  public LearningFlowService(
      FeatureTemplateRepository templateRepository,
      LearningFlowConfigRepository configRepository,
      LearningFlowNodeRepository nodeRepository,
      LearningFlowRunRepository runRepository,
      LearningFlowRunNodeRepository runNodeRepository,
      IdempotencyRecordRepository idempotencyRepository,
      ObjectMapper objectMapper) {
    this.templateRepository = templateRepository;
    this.configRepository = configRepository;
    this.nodeRepository = nodeRepository;
    this.runRepository = runRepository;
    this.runNodeRepository = runNodeRepository;
    this.idempotencyRepository = idempotencyRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void initializeForUser(String userId) {
    if (configRepository.findByUserIdAndActiveTrue(userId).isPresent()) {
      return;
    }
    createFlow(userId, 1, DEFAULT_FLOW);
  }

  @Transactional(readOnly = true)
  public List<TemplateResponse> getTemplates() {
    return templateRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
        .map(LearningFlowService::toTemplateResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public FlowResponse getFlow(String userId) {
    CurrentFlow flow = getCurrentFlow(userId);
    return new FlowResponse(
        flow.flowId(), flow.version(), flow.currentPosition(), flow.status(), flow.nodes());
  }

  @Transactional(readOnly = true)
  public CurrentFlow getCurrentFlow(String userId) {
    LearningFlowConfigEntity config =
        configRepository.findByUserIdAndActiveTrue(userId).orElseThrow(() -> notFound("学习流程不存在"));
    LearningFlowRunEntity run =
        runRepository.findByUserIdAndActiveTrue(userId).orElseThrow(() -> notFound("学习流程运行记录不存在"));
    return buildCurrentFlow(config, run);
  }

  @Transactional
  public FlowResponse saveFlow(String userId, SaveFlowRequest request) {
    LearningFlowConfigEntity currentConfig =
        configRepository.findActiveForUpdate(userId).orElseThrow(() -> notFound("学习流程不存在"));
    if (currentConfig.getVersion() != request.version()) {
      throw new ApiException(HttpStatus.CONFLICT, "FLOW_VERSION_CONFLICT", "学习流程已在其他设备更新，请刷新后重试");
    }

    List<String> templateCodes =
        request.nodes().stream().map(node -> node.templateCode().trim()).toList();
    validateTemplates(templateCodes);

    runRepository.findByUserIdAndActiveTrue(userId).ifPresent(LearningFlowRunEntity::deactivate);
    currentConfig.deactivate();
    CurrentFlow newFlow = createFlow(userId, currentConfig.getVersion() + 1, templateCodes);
    return new FlowResponse(
        newFlow.flowId(),
        newFlow.version(),
        newFlow.currentPosition(),
        newFlow.status(),
        newFlow.nodes());
  }

  @Transactional
  public CompleteFlowResponse completeCurrent(
      String userId, String idempotencyKey, CompleteFlowRequest request) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "缺少 Idempotency-Key");
    }
    if (idempotencyKey.length() > 128) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key 过长");
    }

    LearningFlowRunEntity run =
        runRepository.findActiveForUpdate(userId).orElseThrow(() -> notFound("学习流程运行记录不存在"));
    var existing =
        idempotencyRepository.findByUserIdAndScopeAndIdempotencyKeyAndExpiresAtAfter(
            userId, COMPLETE_SCOPE, idempotencyKey, Instant.now());
    if (existing.isPresent()) {
      return readStoredResponse(existing.orElseThrow().getResponseJson());
    }

    List<LearningFlowRunNodeEntity> runNodes =
        runNodeRepository.findByRunIdOrderByPositionAsc(run.getId());
    int currentPosition = run.getCurrentPosition();
    if (currentPosition >= runNodes.size()) {
      throw new ApiException(HttpStatus.CONFLICT, "FLOW_NODE_MISMATCH", "当前学习流程已经完成");
    }

    LearningFlowRunNodeEntity currentNode = runNodes.get(currentPosition);
    if (!currentNode.getSourceNodeId().equals(request.flowNodeId())) {
      throw new ApiException(HttpStatus.CONFLICT, "FLOW_NODE_MISMATCH", "完成的不是当前流程节点");
    }

    currentNode.complete();
    boolean flowCompleted = currentPosition + 1 >= runNodes.size();
    run.advance(flowCompleted);

    NextNodeResponse nextNode =
        flowCompleted ? null : toNextNode(runNodes.get(currentPosition + 1), templateMap());
    var response =
        new CompleteFlowResponse(
            currentNode.getSourceNodeId(), flowCompleted, currentPosition + 1, nextNode);
    storeResponse(userId, idempotencyKey, response);
    return response;
  }

  private CurrentFlow createFlow(String userId, long version, List<String> templateCodes) {
    String configId = newId("flow");
    var config = configRepository.save(new LearningFlowConfigEntity(configId, userId, version));

    List<LearningFlowNodeEntity> nodes = new ArrayList<>();
    for (int position = 0; position < templateCodes.size(); position++) {
      nodes.add(
          new LearningFlowNodeEntity(newId("fn"), configId, templateCodes.get(position), position));
    }
    nodeRepository.saveAll(nodes);

    var run =
        runRepository.save(
            new LearningFlowRunEntity(newId("run"), userId, configId, config.getVersion()));
    List<LearningFlowRunNodeEntity> runNodes = new ArrayList<>();
    for (LearningFlowNodeEntity node : nodes) {
      runNodes.add(
          new LearningFlowRunNodeEntity(
              newId("rn"), run.getId(), node.getId(), node.getTemplateCode(), node.getPosition()));
    }
    runNodeRepository.saveAll(runNodes);
    return buildCurrentFlow(config, run);
  }

  private CurrentFlow buildCurrentFlow(LearningFlowConfigEntity config, LearningFlowRunEntity run) {
    List<LearningFlowNodeEntity> nodes =
        nodeRepository.findByConfigIdOrderByPositionAsc(config.getId());
    List<LearningFlowRunNodeEntity> runNodes =
        runNodeRepository.findByRunIdOrderByPositionAsc(run.getId());
    var runNodeBySource =
        runNodes.stream()
            .collect(
                Collectors.toMap(LearningFlowRunNodeEntity::getSourceNodeId, Function.identity()));
    var templates = templateMap();
    List<FlowNodeResponse> responses =
        nodes.stream()
            .map(node -> toFlowNode(node, runNodeBySource.get(node.getId()), templates))
            .toList();
    List<String> runNodeIds = runNodes.stream().map(LearningFlowRunNodeEntity::getId).toList();
    return new CurrentFlow(
        config.getId(),
        config.getVersion(),
        run.getId(),
        run.getCurrentPosition(),
        run.getStatus(),
        responses,
        runNodeIds);
  }

  private void validateTemplates(List<String> templateCodes) {
    var enabled = templateMap();
    for (String templateCode : templateCodes) {
      if (!enabled.containsKey(templateCode)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "学习流程包含无效功能模板");
      }
    }
  }

  private HashMap<String, FeatureTemplateEntity> templateMap() {
    return templateRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
        .collect(
            Collectors.toMap(
                FeatureTemplateEntity::getCode,
                Function.identity(),
                (left, right) -> left,
                HashMap::new));
  }

  private void storeResponse(String userId, String idempotencyKey, CompleteFlowResponse response) {
    try {
      idempotencyRepository.save(
          new IdempotencyRecordEntity(
              newId("idem"),
              userId,
              COMPLETE_SCOPE,
              idempotencyKey,
              objectMapper.writeValueAsString(response),
              Instant.now().plus(Duration.ofHours(24))));
    } catch (JacksonException exception) {
      throw new IllegalStateException("无法保存幂等响应", exception);
    }
  }

  private CompleteFlowResponse readStoredResponse(String responseJson) {
    try {
      return objectMapper.readValue(responseJson, CompleteFlowResponse.class);
    } catch (JacksonException exception) {
      throw new IllegalStateException("无法读取幂等响应", exception);
    }
  }

  private static FlowNodeResponse toFlowNode(
      LearningFlowNodeEntity node,
      LearningFlowRunNodeEntity runNode,
      HashMap<String, FeatureTemplateEntity> templates) {
    FeatureTemplateEntity template = templates.get(node.getTemplateCode());
    String status = runNode == null ? "NOT_STARTED" : runNode.getStatus();
    return new FlowNodeResponse(
        node.getId(),
        node.getTemplateCode(),
        template.getName(),
        node.getPosition(),
        status,
        "COMPLETED".equals(status),
        template.getRoute());
  }

  private static NextNodeResponse toNextNode(
      LearningFlowRunNodeEntity node, HashMap<String, FeatureTemplateEntity> templates) {
    FeatureTemplateEntity template = templates.get(node.getTemplateCode());
    return new NextNodeResponse(
        node.getSourceNodeId(), node.getTemplateCode(), template.getName(), template.getRoute());
  }

  private static TemplateResponse toTemplateResponse(FeatureTemplateEntity template) {
    return new TemplateResponse(
        template.getCode(),
        template.getName(),
        template.getShortName(),
        template.getRoute(),
        template.getIcon(),
        template.isEnabled(),
        template.isRepeatable());
  }

  private static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
  }

  private static String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
