package com.inno.orderservice.configuration;

import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestContextHolder;

public class ContextCopyingTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    var requestAttributes = RequestContextHolder.getRequestAttributes();

    return () -> {
      try {
        if (requestAttributes != null) {
          RequestContextHolder.setRequestAttributes(requestAttributes, true);
        }
        runnable.run();
      } finally {
        RequestContextHolder.resetRequestAttributes();
      }
    };
  }
}
