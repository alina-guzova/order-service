package com.inno.orderservice.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
@RequiredArgsConstructor
public class FeignErrorDecoder implements ErrorDecoder {

  private final ErrorDecoder defaultErrorDecoder = new Default();
  private final ObjectMapper objectMapper;

  @Override
  public Exception decode(String methodKey, Response response) {
    if (response.status() >= 500 && response.status() < 600) {
      return defaultErrorDecoder.decode(methodKey, response);
    }

    if (response.body() != null) {
      try (InputStream bodyIs = response.body().asInputStream()) {

        ExceptionResponseDTO errorResponse =
            objectMapper.readValue(bodyIs, ExceptionResponseDTO.class);
        HttpStatus status = HttpStatus.valueOf(response.status());
        String message =
            errorResponse.message() != null ? errorResponse.message() : "Remote service error";

        return new BaseAppException(message, status, errorResponse.details());

      } catch (IOException e) {
        log.error("Failed to parse Feign error response body from method {}", methodKey, e);
      }
    }

    return defaultErrorDecoder.decode(methodKey, response);
  }
}
