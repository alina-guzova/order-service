package com.inno.orderservice.exception;

import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BaseAppException extends RuntimeException {

  private final HttpStatus status;
  private final List<String> details;

  public BaseAppException(String message, HttpStatus status) {
    this(message, status, null);
  }

  public BaseAppException(String message, HttpStatus status, List<String> details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}
