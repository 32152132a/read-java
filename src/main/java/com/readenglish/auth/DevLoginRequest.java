package com.readenglish.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DevLoginRequest(
    @NotBlank(message = "昵称不能为空") @Size(max = 30, message = "昵称最多 30 个字符") String nickname) {}
