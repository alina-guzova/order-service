package com.inno.orderservice.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExceptionResponseDTO(
    int status,
    String error,
    String message,
    List<String> details,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime timestamp) {

  public static ExceptionResponseDTO of(HttpStatus status, String message) {
    return new ExceptionResponseDTO(
        status.value(), status.getReasonPhrase(), message, null, LocalDateTime.now());
  }

  public static ExceptionResponseDTO of(HttpStatus status, String message, List<String> details) {
    return new ExceptionResponseDTO(
        status.value(), status.getReasonPhrase(), message, details, LocalDateTime.now());
  }
}
