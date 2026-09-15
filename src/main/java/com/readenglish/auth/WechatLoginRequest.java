package com.readenglish.auth;

import jakarta.validation.constraints.NotBlank;

public record WechatLoginRequest(@NotBlank(message = "微信登录 code 不能为空") String code) {}
