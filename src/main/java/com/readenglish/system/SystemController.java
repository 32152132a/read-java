package com.readenglish.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public final class SystemController {

  @GetMapping("/health")
  @Operation(summary = "业务接口健康检查")
  @SecurityRequirements
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }
}
