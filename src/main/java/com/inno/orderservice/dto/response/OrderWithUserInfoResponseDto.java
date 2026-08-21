package com.inno.orderservice.dto.response;

import lombok.Builder;

@Builder
public record OrderWithUserInfoResponseDto(UserInfoResponseDto userInfo, OrderResponseDto order) {}
