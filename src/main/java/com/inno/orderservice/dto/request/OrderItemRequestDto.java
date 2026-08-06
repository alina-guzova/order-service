package com.inno.orderservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record OrderItemRequestDto(
    @NotNull(message = "Field 'itemId' is required") Long itemId,
    @NotNull(message = "Field 'quantity' is required")
        @Min(value = 1, message = "Min quantity is 1")
        Integer quantity) {}
