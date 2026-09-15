package com.readenglish.auth;

public record LoginResponse(
    String accessToken, String tokenType, long expiresIn, UserProfile user, boolean newUser) {}
