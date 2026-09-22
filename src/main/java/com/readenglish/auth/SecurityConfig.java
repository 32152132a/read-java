package com.readenglish.auth;

import com.readenglish.common.api.ApiResponse;
import com.readenglish.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/api/v1/health",
                        "/api/v1/auth/dev/login",
                        "/api/v1/auth/wechat/login",
                        "/api/v1/auth/refresh",
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeUnauthorized(request, response, objectMapper)));
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(
        java.util.List.of("http://127.0.0.1:5173", "http://localhost:5173"));
    configuration.setAllowedMethods(
        java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        java.util.List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
    configuration.setExposedHeaders(java.util.List.of("X-Request-Id"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  @Bean
  JwtEncoder jwtEncoder(AuthProperties properties) {
    return NimbusJwtEncoder.withSecretKey(secretKey(properties)).build();
  }

  @Bean
  JwtDecoder jwtDecoder(AuthProperties properties) {
    var decoder =
        NimbusJwtDecoder.withSecretKey(secretKey(properties))
            .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
            .build();
    OAuth2TokenValidator<Jwt> issuerValidator =
        JwtValidators.createDefaultWithIssuer(properties.issuer());
    OAuth2TokenValidator<Jwt> tokenUseValidator =
        new JwtClaimValidator<>("token_use", "access"::equals);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(issuerValidator, tokenUseValidator));
    return decoder;
  }

  private static SecretKey secretKey(AuthProperties properties) {
    String secret = properties.jwtSecret();
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("JWT_ACCESS_SECRET must contain at least 32 bytes");
    }
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  private static void writeUnauthorized(
      HttpServletRequest request, HttpServletResponse response, ObjectMapper objectMapper)
      throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    var body =
        ApiResponse.failure(
            "AUTH_TOKEN_INVALID", "登录凭证无效或已过期", RequestIdFilter.getRequestId(request));
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
