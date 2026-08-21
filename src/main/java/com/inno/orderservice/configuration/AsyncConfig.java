package com.inno.orderservice.configuration;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(AsyncProperties.class)
public class AsyncConfig {

  @Bean(name = "orderAsyncPool")
  public Executor orderAsyncPool(AsyncProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.corePoolSize());
    executor.setMaxPoolSize(properties.maxPoolSize());
    executor.setQueueCapacity(properties.queueCapacity());
    executor.setKeepAliveSeconds(properties.keepAliveSeconds());
    executor.setThreadNamePrefix("OrderAsync-");
    executor.setTaskDecorator(new ContextCopyingTaskDecorator());
    executor.initialize();
    return executor;
  }
}
