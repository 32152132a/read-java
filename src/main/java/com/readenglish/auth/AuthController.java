package com.readenglish.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "app.auth", name = "dev-login-enabled", havingValue = "true")
public final class AuthController {

  private static final String DEV_USER_ID = "dev-user";

  private final JwtTokenService jwtTokenService;

  public AuthController(JwtTokenService jwtTokenService) {
    this.jwtTokenService = jwtTokenService;
  }

  @PostMapping("/dev/login")
  @Operation(summary = "本地开发临时登录")
  @SecurityRequirements
  public LoginResponse devLogin(@Valid @RequestBody DevLoginRequest request) {
    var user = new UserProfile(DEV_USER_ID, request.nickname().trim(), null, "US");
    return jwtTokenService.issueFor(user);
  }
}
