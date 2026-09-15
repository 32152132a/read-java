package com.readenglish.user;

import jakarta.validation.constraints.NotBlank;

public record UpdatePreferenceRequest(@NotBlank(message = "发音偏好不能为空") String accentPreference) {}
