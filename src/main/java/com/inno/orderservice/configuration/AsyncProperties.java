package com.inno.orderservice.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.async")
public record AsyncProperties(
    int corePoolSize, int maxPoolSize, int queueCapacity, int keepAliveSeconds) {}
