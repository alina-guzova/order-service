package com.inno.orderservice.configuration;

import static com.inno.orderservice.util.ConfigConstants.TRACE_ID_HEADER;
import static com.inno.orderservice.util.ConfigConstants.TRACE_ID_KEY;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inno.orderservice.exception.FeignErrorDecoder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

  @Bean
  public ErrorDecoder errorDecoder(ObjectMapper objectMapper) {
    return new FeignErrorDecoder(objectMapper);
  }

  @Bean
  public RequestInterceptor requestInterceptor() {
    return requestTemplate -> {
      String traceId = MDC.get(TRACE_ID_KEY);
      if (traceId != null && !traceId.isEmpty()) {
        requestTemplate.header(TRACE_ID_HEADER, traceId);
      }

      ServletRequestAttributes attributes =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

      if (attributes != null) {
        HttpServletRequest request = attributes.getRequest();
        copyHeader(request, requestTemplate, "X-User-Id");
        copyHeader(request, requestTemplate, "X-User-Role");
      }
    };
  }

  private void copyHeader(
      HttpServletRequest sourceRequest, RequestTemplate template, String headerName) {
    String headerValue = sourceRequest.getHeader(headerName);
    if (headerValue != null && !headerValue.isEmpty()) {
      template.header(headerName, headerValue);
    }
  }
}
