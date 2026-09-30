package com.readenglish.user;

import com.readenglish.auth.UserProfile;
import com.readenglish.common.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public final class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
    return userService.getProfile(jwt.getSubject());
  }

  @GetMapping("/me/stats")
  public UserStats stats(@AuthenticationPrincipal Jwt jwt) {
    return userService.getStats(jwt.getSubject());
  }

  @PatchMapping("/me/preferences")
  public UserProfile updatePreferences(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdatePreferenceRequest request) {
    String accent = request.accentPreference().trim().toUpperCase();
    if (!java.util.Set.of("US", "GB").contains(accent)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "PREFERENCE_NOT_SUPPORTED", "发音偏好只能是 US 或 GB");
    }
    return userService.updateAccent(jwt.getSubject(), accent);
  }
}
