package com.readenglish.learningflow;

import com.readenglish.learningflow.LearningFlowModels.CompleteFlowRequest;
import com.readenglish.learningflow.LearningFlowModels.CompleteFlowResponse;
import com.readenglish.learningflow.LearningFlowModels.FlowResponse;
import com.readenglish.learningflow.LearningFlowModels.SaveFlowRequest;
import com.readenglish.learningflow.LearningFlowModels.TemplateResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning-flow")
public class LearningFlowController {

  private final LearningFlowService learningFlowService;

  public LearningFlowController(LearningFlowService learningFlowService) {
    this.learningFlowService = learningFlowService;
  }

  @GetMapping("/templates")
  public List<TemplateResponse> templates() {
    return learningFlowService.getTemplates();
  }

  @GetMapping
  public FlowResponse flow(@AuthenticationPrincipal Jwt jwt) {
    return learningFlowService.getFlow(jwt.getSubject());
  }

  @PutMapping
  public FlowResponse save(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveFlowRequest request) {
    return learningFlowService.saveFlow(jwt.getSubject(), request);
  }

  @PostMapping("/current/complete")
  public CompleteFlowResponse complete(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CompleteFlowRequest request) {
    return learningFlowService.completeCurrent(jwt.getSubject(), idempotencyKey, request);
  }
}
