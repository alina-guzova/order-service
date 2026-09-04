package com.inno.orderservice.unit;

import static com.inno.orderservice.util.OrderTestData.DEFAULT_ORDER_ID;
import static com.inno.orderservice.util.OrderTestData.DEFAULT_PAYMENT_ID;
import static com.inno.orderservice.util.OrderTestData.DEFAULT_TOTAL_PRICE;
import static org.mockito.Mockito.verify;

import com.inno.orderservice.consumer.PaymentEventListener;
import com.inno.orderservice.model.PaymentEvent;
import com.inno.orderservice.model.PaymentStatus;
import com.inno.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

  @Mock private OrderService orderService;

  @InjectMocks private PaymentEventListener paymentEventListener;

  @Test
  void shouldHandlePaymentEventAndCallOrderService() {
    // given
    PaymentEvent event =
        new PaymentEvent(
            DEFAULT_PAYMENT_ID, DEFAULT_ORDER_ID, PaymentStatus.SUCCESS, DEFAULT_TOTAL_PRICE);

    // when
    paymentEventListener.handlePaymentEvent(event);

    // then
    verify(orderService).updateOrderStatus(DEFAULT_ORDER_ID, PaymentStatus.SUCCESS);
  }

  @Test
  void shouldHandleFailedPaymentEventAndCallOrderService() {
    // given
    PaymentEvent event =
        new PaymentEvent(
            DEFAULT_PAYMENT_ID, DEFAULT_ORDER_ID, PaymentStatus.FAILED, DEFAULT_TOTAL_PRICE);

    // when
    paymentEventListener.handlePaymentEvent(event);

    // then
    verify(orderService).updateOrderStatus(DEFAULT_ORDER_ID, PaymentStatus.FAILED);
  }
}
