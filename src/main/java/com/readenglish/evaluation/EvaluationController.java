package com.readenglish.evaluation;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {
  private final EvaluationService service;

  public EvaluationController(EvaluationService service) {
    this.service = service;
  }

  @PostMapping
  public EvaluationModels.Response create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody EvaluationModels.Create input) {
    return service.create(jwt.getSubject(), key, input);
  }

  @GetMapping("/{id}")
  public EvaluationModels.Response get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    return service.get(jwt.getSubject(), id);
  }
}
