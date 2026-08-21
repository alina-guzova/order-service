package com.inno.orderservice.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebLoggingFilter extends OncePerRequestFilter {

  private static final String TRACE_ID_KEY = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    long startTime = System.currentTimeMillis();

    String traceId = UUID.randomUUID().toString().substring(0, 8);
    MDC.put(TRACE_ID_KEY, traceId);

    String requestUri = request.getRequestURI();
    if (request.getQueryString() != null) {
      requestUri += "?" + request.getQueryString();
    }

    log.info("Incoming HTTP {} {}", request.getMethod(), requestUri);

    try {
      filterChain.doFilter(request, response);
    } finally {
      long duration = System.currentTimeMillis() - startTime;
      log.info(
          "Outgoing HTTP {} {} | Status: {} | Duration: {}ms",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          duration);

      MDC.clear();
    }
  }
}
