package com.readenglish.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-Request-Id";
  private static final String ATTRIBUTE_NAME = RequestIdFilter.class.getName() + ".requestId";
  private static final String MDC_NAME = "requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var requestId = UUID.randomUUID().toString();
    request.setAttribute(ATTRIBUTE_NAME, requestId);
    response.setHeader(HEADER_NAME, requestId);
    MDC.put(MDC_NAME, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_NAME);
    }
  }

  public static String getRequestId(HttpServletRequest request) {
    var requestId = request.getAttribute(ATTRIBUTE_NAME);
    return requestId instanceof String value ? value : UUID.randomUUID().toString();
  }
}
