package com.readenglish.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(String issuer, Duration accessTokenTtl, String jwtSecret) {}
