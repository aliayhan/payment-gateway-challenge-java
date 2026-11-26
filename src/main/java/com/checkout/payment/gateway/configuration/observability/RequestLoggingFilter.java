package com.checkout.payment.gateway.configuration.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every response carries a correlationID and logs a basic summary at end of a request.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

  public static final String CORRELATION_ID_HEADER = "X-Request-Id";
  public static final String CORRELATION_ID = "correlationId";

  private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String correlationId = resolveCorrelationId(
        request.getHeader(CORRELATION_ID_HEADER));
    MDC.put(CORRELATION_ID, correlationId);
    response.setHeader(CORRELATION_ID_HEADER, correlationId);

    long startNanos = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

      LOG.info(
          "request_summary correlationId={} method={} route={} status={} durationMs={}",
          correlationId,
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          durationMs);

      MDC.remove(CORRELATION_ID);
    }
  }

  private static String resolveCorrelationId(String correlationId) {
    if (correlationId != null && !correlationId.isBlank()) {
      return correlationId;
    }
    return UUID.randomUUID().toString();
  }
}
