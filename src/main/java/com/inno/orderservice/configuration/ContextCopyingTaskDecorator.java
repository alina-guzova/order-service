package com.inno.orderservice.configuration;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestContextHolder;

public class ContextCopyingTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    var requestAttributes = RequestContextHolder.getRequestAttributes();
    Map<String, String> mdcContextMap = MDC.getCopyOfContextMap();

    return () -> {
      try {
        if (requestAttributes != null) {
          RequestContextHolder.setRequestAttributes(requestAttributes, true);
        }

        if (mdcContextMap != null) {
          MDC.setContextMap(mdcContextMap);
        } else {
          MDC.clear();
        }

        runnable.run();
      } finally {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
      }
    };
  }
}
