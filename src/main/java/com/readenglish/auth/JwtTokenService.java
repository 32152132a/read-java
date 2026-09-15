package com.readenglish.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public final class JwtTokenService {

  private final AuthProperties properties;
  private final JwtEncoder jwtEncoder;

  public JwtTokenService(AuthProperties properties, JwtEncoder jwtEncoder) {
    this.properties = properties;
    this.jwtEncoder = jwtEncoder;
  }

  public LoginResponse issueFor(UserProfile user, String refreshToken, boolean newUser) {
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
    var header = JwsHeader.with(MacAlgorithm.HS256).build();
    var claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(user.id())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim("nickname", user.nickname())
            .claim("accent", user.accentPreference())
            .claim("token_use", "access")
            .build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new LoginResponse(
        token, refreshToken, "Bearer", properties.accessTokenTtl().toSeconds(), user, newUser);
  }
}
