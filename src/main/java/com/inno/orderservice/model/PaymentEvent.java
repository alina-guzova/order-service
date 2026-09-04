package com.inno.orderservice.model;

public record PaymentEvent(
    String paymentId, Long orderId, PaymentStatus status, Double paymentAmount) {}
