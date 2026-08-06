package com.inno.orderservice.integration;

import com.inno.orderservice.configuration.FeignConfig;
import com.inno.orderservice.dto.response.UserInfoResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "user-service",
    url = "${application.integration.user-service.url}",
    configuration = FeignConfig.class)
public interface UserServiceClient {

  @GetMapping("/api/v1/users/{userId}")
  UserInfoResponseDto getUserInfoById(@PathVariable("userId") Long userId);
}
