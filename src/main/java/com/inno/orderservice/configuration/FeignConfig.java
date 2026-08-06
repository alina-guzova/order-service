package com.inno.orderservice.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inno.orderservice.exception.FeignErrorDecoder;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
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
  public RequestInterceptor bearerTokenRequestInterceptor() {
    return requestTemplate -> {
      ServletRequestAttributes attributes =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

      if (attributes != null) {
        HttpServletRequest request = attributes.getRequest();
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && !authHeader.isBlank()) {
          requestTemplate.header("Authorization", authHeader);
        }
      }
    };
  }
}
