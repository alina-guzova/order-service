package com.inno.orderservice.dto.request;

import com.inno.orderservice.entity.OrderStatus;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;
import lombok.Builder;
import org.springframework.format.annotation.DateTimeFormat;

@Builder
public record OrderFilterDto(
    @PastOrPresent @DateTimeFormat(pattern = "dd.MM.yyyy") LocalDate minDate,
    @PastOrPresent @DateTimeFormat(pattern = "dd.MM.yyyy") LocalDate maxDate,
    OrderStatus status) {}
