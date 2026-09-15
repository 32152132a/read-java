package com.readenglish.common.api;

import org.springframework.http.HttpStatus;

public final class ApiException extends RuntimeException {

  private final String code;
  private final HttpStatus status;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
