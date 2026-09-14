package com.inno.orderservice.consumer;

import com.inno.orderservice.model.PaymentEvent;
import com.inno.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

  private final OrderService orderService;

  @KafkaListener(
      topics = "${spring.kafka.template.topic-payment}",
      groupId = "${spring.kafka.consumer.group-id}")
  public void handlePaymentEvent(PaymentEvent event) {
    log.info("Received payment event for order: {}", event.orderId());
    orderService.updateOrderStatus(event.orderId(), event.status());
  }
}
