package com.readenglish.auth;

import com.readenglish.common.api.ApiException;
import com.readenglish.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController {

  private final JwtTokenService jwtTokenService;
  private final UserOnboardingService onboardingService;
  private final RefreshTokenService refreshTokenService;
  private final UserService userService;
  private final AuthProperties authProperties;

  public AuthController(
      JwtTokenService jwtTokenService,
      UserOnboardingService onboardingService,
      RefreshTokenService refreshTokenService,
      UserService userService,
      AuthProperties authProperties) {
    this.jwtTokenService = jwtTokenService;
    this.onboardingService = onboardingService;
    this.refreshTokenService = refreshTokenService;
    this.userService = userService;
    this.authProperties = authProperties;
  }

  @PostMapping("/dev/login")
  @Operation(summary = "本地开发临时登录")
  @SecurityRequirements
  public LoginResponse devLogin(@Valid @RequestBody DevLoginRequest request) {
    if (!authProperties.devLoginEnabled()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "请求的资源不存在");
    }
    var result = onboardingService.initializeDevelopmentUser(request.nickname().trim());
    return issueLogin(result.profile(), result.newUser());
  }

  @PostMapping("/wechat/login")
  @Operation(summary = "微信小程序登录")
  @SecurityRequirements
  public LoginResponse wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
    var result = onboardingService.initializeWechatUser(request.code().trim());
    return issueLogin(result.profile(), result.newUser());
  }

  @PostMapping("/refresh")
  @Operation(summary = "刷新访问令牌")
  @SecurityRequirements
  public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    var rotation = refreshTokenService.rotate(request.refreshToken());
    var user = userService.getProfile(rotation.userId());
    return jwtTokenService.issueFor(user, rotation.refreshToken(), false);
  }

  @PostMapping("/logout")
  @Operation(summary = "退出登录并撤销刷新令牌")
  public LogoutResponse logout(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RefreshTokenRequest request) {
    refreshTokenService.revoke(request.refreshToken(), jwt.getSubject());
    return new LogoutResponse(true);
  }

  private LoginResponse issueLogin(UserProfile user, boolean newUser) {
    String refreshToken = refreshTokenService.issue(user.id());
    return jwtTokenService.issueFor(user, refreshToken, newUser);
  }
}
