package com.inno.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Builder;

@Builder
public record OrderCreateRequestDto(
    @NotEmpty(message = "List 'items' is required") @Valid List<OrderItemRequestDto> items) {}
