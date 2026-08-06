package com.inno.orderservice.dto.response;

import lombok.Builder;

@Builder
public record OrderItemResponseDto(Long itemId, Integer quantity) {}
