package com.inno.orderservice.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record UserInfoResponseDto(
    Long id,
    String name,
    String surname,
    LocalDate birthDate,
    String email,
    Boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
