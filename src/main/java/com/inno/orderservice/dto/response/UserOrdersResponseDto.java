package com.inno.orderservice.dto.response;

import java.util.List;
import lombok.Builder;

@Builder
public record UserOrdersResponseDto(UserInfoResponseDto userInfo, List<OrderResponseDto> orders) {}
