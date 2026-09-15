package com.readenglish.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "com.readenglish")
public final class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

  private final HttpServletRequest request;

  public ApiResponseAdvice(HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return !ApiResponse.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      org.springframework.http.server.ServerHttpRequest serverRequest,
      org.springframework.http.server.ServerHttpResponse serverResponse) {
    return ApiResponse.success(body, RequestIdFilter.getRequestId(request));
  }
}
