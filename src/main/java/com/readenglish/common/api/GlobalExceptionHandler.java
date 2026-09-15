package com.readenglish.common.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("请求参数不正确");
    var body =
        ApiResponse.failure("VALIDATION_ERROR", message, RequestIdFilter.getRequestId(request));
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
      ConstraintViolationException exception, HttpServletRequest request) {
    String message =
        exception.getConstraintViolations().stream()
            .findFirst()
            .map(violation -> violation.getMessage())
            .orElse("请求参数不正确");
    var body =
        ApiResponse.failure("VALIDATION_ERROR", message, RequestIdFilter.getRequestId(request));
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    var body =
        ApiResponse.failure("VALIDATION_ERROR", "请求体格式不正确", RequestIdFilter.getRequestId(request));
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(
      NoResourceFoundException exception, HttpServletRequest request) {
    var body =
        ApiResponse.failure(
            "RESOURCE_NOT_FOUND", "请求的资源不存在", RequestIdFilter.getRequestId(request));
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
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
