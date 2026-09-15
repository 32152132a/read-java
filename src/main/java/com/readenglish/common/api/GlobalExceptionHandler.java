package com.readenglish.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.readenglish")
public final class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApiException(
      ApiException exception, HttpServletRequest request) {
    var body =
        ApiResponse.failure(
            exception.getCode(), exception.getMessage(), RequestIdFilter.getRequestId(request));
    return ResponseEntity.status(exception.getStatus()).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
      Exception exception, HttpServletRequest request) {
    var requestId = RequestIdFilter.getRequestId(request);
    log.error("Unhandled request failure, requestId={}", requestId, exception);
    var body = ApiResponse.failure("INTERNAL_ERROR", "服务暂时不可用，请稍后重试", requestId);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
