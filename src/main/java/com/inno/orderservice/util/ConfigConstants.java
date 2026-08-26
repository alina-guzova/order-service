package com.inno.orderservice.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ConfigConstants {

  // Logging
  public static final String TRACE_ID_KEY = "traceId";
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  // Gateway headers
  public static final String USER_ID_HEADER = "X-User-Id";
  public static final String USER_ROLE_HEADER = "X-User-Role";
}
