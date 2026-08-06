package com.inno.orderservice.util;

import com.inno.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("orderSecurity")
@RequiredArgsConstructor
public class OrderSecurity {

  private final OrderRepository orderRepository;

  public boolean isOwner(Long orderId, Long currentUserId) {
    return orderRepository
        .findById(orderId)
        .map(order -> order.getUserId().equals(currentUserId))
        .orElse(false);
  }
}
