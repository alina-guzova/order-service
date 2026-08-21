package com.inno.orderservice.dto.response;

import com.inno.orderservice.entity.OrderStatus;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;

@Builder
public record OrderResponseDto(
    Long id,
    OrderStatus status,
    Double totalPrice,
    List<OrderItemResponseDto> items,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
