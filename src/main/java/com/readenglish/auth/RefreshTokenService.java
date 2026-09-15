package com.readenglish.auth;

import com.readenglish.common.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

  private final SecureRandom secureRandom = new SecureRandom();
  private final RefreshTokenRepository refreshTokenRepository;
  private final AuthProperties authProperties;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository, AuthProperties authProperties) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.authProperties = authProperties;
  }

  @Transactional
  public String issue(String userId) {
    String token = generateToken();
    refreshTokenRepository.save(
        new RefreshTokenEntity(
            "rt_" + UUID.randomUUID().toString().replace("-", ""),
            userId,
            hash(token),
            Instant.now().plus(authProperties.refreshTokenTtl())));
    return token;
  }

  @Transactional
  public Rotation rotate(String rawToken) {
    RefreshTokenEntity current = findValid(rawToken);
    current.revoke();
    return new Rotation(current.getUserId(), issue(current.getUserId()));
  }

  @Transactional
  public void revoke(String rawToken, String authenticatedUserId) {
    RefreshTokenEntity token = findValid(rawToken);
    if (!token.getUserId().equals(authenticatedUserId)) {
      throw invalidToken();
    }
    token.revoke();
  }

  private RefreshTokenEntity findValid(String rawToken) {
    RefreshTokenEntity token =
        refreshTokenRepository.findByTokenHash(hash(rawToken)).orElseThrow(this::invalidToken);
    if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
      throw invalidToken();
    }
    return token;
  }

  private String generateToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 不可用", exception);
    }
  }

  private ApiException invalidToken() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "刷新令牌无效或已过期");
  }

  public record Rotation(String userId, String refreshToken) {}
}
