package com.readenglish.learningcontent;

import com.readenglish.learningcontent.LearningStageModels.LearningSessionResponse;
import com.readenglish.learningcontent.LearningStageModels.PositionResponse;
import com.readenglish.learningcontent.LearningStageModels.UpdatePositionRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning-stages")
public class LearningStageController {

  private final LearningStageService learningStageService;

  public LearningStageController(LearningStageService learningStageService) {
    this.learningStageService = learningStageService;
  }

  @GetMapping("/{templateCode}/session")
  public LearningSessionResponse session(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String templateCode,
      @RequestParam(required = false) String flowNodeId,
      @RequestParam(defaultValue = "false") boolean review) {
    return learningStageService.getSession(jwt.getSubject(), templateCode, flowNodeId, review);
  }

  @PutMapping("/sessions/{sessionId}/position")
  public PositionResponse updatePosition(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String sessionId,
      @Valid @RequestBody UpdatePositionRequest request) {
    return learningStageService.updatePosition(jwt.getSubject(), sessionId, request);
  }
}
