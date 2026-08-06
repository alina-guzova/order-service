package com.inno.orderservice.exception.handler;

import com.inno.orderservice.exception.BaseAppException;
import com.inno.orderservice.exception.ExceptionResponseDTO;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BaseAppException.class)
  public ResponseEntity<ExceptionResponseDTO> handleBaseAppException(BaseAppException ex) {
    ExceptionResponseDTO response =
        ExceptionResponseDTO.of(ex.getStatus(), ex.getMessage(), ex.getDetails());

    return ResponseEntity.status(ex.getStatus()).body(response);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ExceptionResponseDTO> handleValidationException(
      MethodArgumentNotValidException ex) {
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getDefaultMessage())
            .toList();

    log.warn("Validation failed | errorsCount={}, details={}", errors.size(), errors);

    ExceptionResponseDTO response =
        ExceptionResponseDTO.of(HttpStatus.BAD_REQUEST, "Validation failed", errors);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ExceptionResponseDTO> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex) {
    log.warn("Failed to read HTTP message: invalid JSON or malformed payload");

    ExceptionResponseDTO response =
        ExceptionResponseDTO.of(
            HttpStatus.BAD_REQUEST, "Incorrect JSON request or invalid data format");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ExceptionResponseDTO> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex) {
    log.warn("HTTP method not supported | method={}", ex.getMethod());

    ExceptionResponseDTO response =
        ExceptionResponseDTO.of(
            HttpStatus.METHOD_NOT_ALLOWED,
            "HTTP method '%s' is not supported for this endpoint".formatted(ex.getMethod()));

    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ExceptionResponseDTO> handleMissingParams(
      MissingServletRequestParameterException ex) {
    log.warn("Missing required request parameter | paramName={}", ex.getParameterName());

    ExceptionResponseDTO response =
        ExceptionResponseDTO.of(
            HttpStatus.BAD_REQUEST,
            "Required parameter '%s' is missing".formatted(ex.getParameterName()));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ExceptionResponseDTO> handleAuthenticationException(
      AuthenticationException ex) {
    ExceptionResponseDTO response =
        ExceptionResponseDTO.of(
            HttpStatus.UNAUTHORIZED, "Full authentication is required to access this resource");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ExceptionResponseDTO> handleAccessDeniedException(
      AccessDeniedException ex) {
    ExceptionResponseDTO response =
        ExceptionResponseDTO.of(HttpStatus.FORBIDDEN, "Access denied: insufficient permissions");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ExceptionResponseDTO> handleGenericException(Exception ex) {
    log.error("Unhandled exception occurred: ", ex);
    ExceptionResponseDTO response =
        ExceptionResponseDTO.of(HttpStatus.INTERNAL_SERVER_ERROR, "Server internal error");

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }
}
