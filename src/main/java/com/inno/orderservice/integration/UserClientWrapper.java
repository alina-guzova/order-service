package com.inno.orderservice.integration;

import com.inno.orderservice.dto.response.UserInfoResponseDto;
import com.inno.orderservice.exception.BaseAppException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserClientWrapper {

  private final UserServiceClient userServiceClient;

  @CircuitBreaker(name = "userServiceClient", fallbackMethod = "getUserInfoFallback")
  public UserInfoResponseDto getUserInfo(Long userId) {
    return userServiceClient.getUserInfoById(userId);
  }

  public UserInfoResponseDto getUserInfoFallback(Long userId, Throwable t) {
    if (t instanceof BaseAppException) {
      throw (BaseAppException) t;
    }
    return UserInfoResponseDto.builder().id(userId).build();
  }
}
