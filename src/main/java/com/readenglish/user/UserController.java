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

  @GetMapping("/me")
  public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
    return profileFrom(jwt, jwt.getClaimAsString("accent"));
  }

  @PatchMapping("/me/preferences")
  public UserProfile updatePreferences(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdatePreferenceRequest request) {
    if (!"US".equals(request.accentPreference())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "PREFERENCE_NOT_SUPPORTED", "当前仅支持美式英语发音");
    }
    return profileFrom(jwt, request.accentPreference());
  }

  private static UserProfile profileFrom(Jwt jwt, String accentPreference) {
    return new UserProfile(
        jwt.getSubject(), jwt.getClaimAsString("nickname"), null, accentPreference);
  }
}
