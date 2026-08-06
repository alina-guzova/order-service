package com.inno.orderservice.configuration;

import feign.FeignException;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CircuitBreakerRegistryConfig {

  @Bean
  public CircuitBreakerConfigCustomizer userServiceClientCircuitBreakerCustomizer() {
    return CircuitBreakerConfigCustomizer.of(
        "userServiceClient",
        builder ->
            builder.recordException(
                throwable -> {
                  if (throwable instanceof FeignException fe) {
                    return fe.status() >= 500 || fe.status() < 0;
                  }
                  return false;
                }));
  }
}
