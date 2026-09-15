package com.readenglish.auth;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserProfile user,
    boolean newUser) {}
