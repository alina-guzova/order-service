package com.inno.orderservice.exception;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;

@UtilityClass
public class BaseAppExceptionBuilder {

  public static BaseAppException buildNotFoundException(String messagePattern, Object... args) {
    return new BaseAppException(messagePattern.formatted(args), HttpStatus.NOT_FOUND);
  }

  public static BaseAppException buildConflictException(String messagePattern, Object... args) {
    return new BaseAppException(messagePattern.formatted(args), HttpStatus.CONFLICT);
  }

  public static BaseAppException buildBadRequestException(String messagePattern, Object... args) {
    return new BaseAppException(messagePattern.formatted(args), HttpStatus.BAD_REQUEST);
  }
}
