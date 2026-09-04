package com.inno.orderservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.inno.orderservice.dto.request.OrderCreateRequestDto;
import com.inno.orderservice.dto.request.OrderFilterDto;
import com.inno.orderservice.dto.request.OrderItemRequestDto;
import com.inno.orderservice.dto.request.OrderUpdateRequestDto;
import com.inno.orderservice.dto.response.OrderResponseDto;
import com.inno.orderservice.dto.response.OrderWithUserInfoResponseDto;
import com.inno.orderservice.dto.response.UserInfoResponseDto;
import com.inno.orderservice.dto.response.UserOrdersResponseDto;
import com.inno.orderservice.entity.Item;
import com.inno.orderservice.entity.Order;
import com.inno.orderservice.entity.OrderItem;
import com.inno.orderservice.entity.OrderStatus;
import com.inno.orderservice.exception.BaseAppException;
import com.inno.orderservice.integration.UserClientWrapper;
import com.inno.orderservice.mapper.OrderMapper;
import com.inno.orderservice.repository.ItemRepository;
import com.inno.orderservice.repository.OrderRepository;
import com.inno.orderservice.service.impl.OrderServiceImpl;
import com.inno.orderservice.util.OrderTestData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

  @Mock private OrderRepository orderRepository;
  @Mock private ItemRepository itemRepository;
  @Mock private UserClientWrapper userClientWrapper;
  @Mock private OrderMapper orderMapper;
  @Mock private Executor orderAsyncPool;

  @InjectMocks private OrderServiceImpl orderService;

  @BeforeEach
  void setUp() {
    lenient()
        .doAnswer(
            invocation -> {
              Runnable task = invocation.getArgument(0);
              task.run();
              return null;
            })
        .when(orderAsyncPool)
        .execute(any(Runnable.class));
  }

  @Nested
  class CreateTests {

    @Test
    void whenCreateOrder_thenOrderIsSavedAndReturned() {
      // given
      Long userId = OrderTestData.DEFAULT_USER_ID;
      OrderCreateRequestDto requestDto = OrderTestData.buildCreateRequestDto();
      UserInfoResponseDto userInfo = OrderTestData.buildUserInfoResponseDto();
      Item item = OrderTestData.buildItemEntity();
      Order mappedOrder = OrderTestData.buildOrderEntity();
      Order savedOrder = OrderTestData.buildOrderEntity();
      OrderItem mappedOrderItem = OrderTestData.buildOrderItemEntity(mappedOrder, item);
      List<OrderItem> orderItems = List.of(mappedOrderItem);
      OrderWithUserInfoResponseDto expectedResponse =
          OrderTestData.buildOrderWithUserInfoResponseDto();

      when(userClientWrapper.getUserInfo(userId)).thenReturn(userInfo);
      when(itemRepository.findAllById(any())).thenReturn(List.of(item));
      when(orderMapper.toOrderItem(any(OrderItemRequestDto.class), eq(item)))
          .thenReturn(mappedOrderItem);
      when(orderMapper.toOrder(
              userId, orderItems, OrderTestData.DEFAULT_TOTAL_PRICE, OrderStatus.PENDING))
          .thenReturn(mappedOrder);
      when(orderRepository.save(mappedOrder)).thenReturn(savedOrder);
      when(orderMapper.toOrderWithUserInfoResponseDto(savedOrder, userInfo))
          .thenReturn(expectedResponse);

      // when
      OrderWithUserInfoResponseDto actualResponse = orderService.create(userId, requestDto);

      // then
      assertThat(actualResponse).isNotNull().isEqualTo(expectedResponse);
      assertThat(mappedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
      assertThat(mappedOrder.getTotalPrice()).isEqualTo(OrderTestData.DEFAULT_TOTAL_PRICE);

      verify(userClientWrapper).getUserInfo(userId);
      verify(itemRepository).findAllById(any());
      verify(orderMapper).toOrder(eq(userId), any(), anyDouble(), eq(OrderStatus.PENDING));
      verify(orderMapper).toOrderItem(any(OrderItemRequestDto.class), eq(item));
      verify(orderRepository).save(mappedOrder);
      verify(orderMapper).toOrderWithUserInfoResponseDto(savedOrder, userInfo);
    }

    @Test
    void whenCreateOrderWithNonExistentItem_thenThrowNotFoundException() {
      // given
      Long userId = OrderTestData.DEFAULT_USER_ID;

      OrderItemRequestDto itemRequestDto =
          OrderTestData.buildItemRequestDto(
              OrderTestData.NON_EXISTENT_ITEM_ID, OrderTestData.DEFAULT_QUANTITY);
      OrderCreateRequestDto requestDto =
          OrderTestData.buildCreateRequestDto(List.of(itemRequestDto));

      UserInfoResponseDto userInfo = OrderTestData.buildUserInfoResponseDto();

      when(userClientWrapper.getUserInfo(userId)).thenReturn(userInfo);
      when(itemRepository.findAllById(any())).thenReturn(Collections.emptyList());

      // when & then
      BaseAppException exception =
          assertThrows(BaseAppException.class, () -> orderService.create(userId, requestDto));

      assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

      verify(userClientWrapper).getUserInfo(userId);
      verify(itemRepository).findAllById(any());
      verifyNoMoreInteractions(itemRepository);
      verifyNoInteractions(orderRepository);
    }

    @Test
    void whenCreateOrderWithDuplicateItems_thenThrowConflictException() {
      // given
      Long userId = OrderTestData.DEFAULT_USER_ID;

      OrderCreateRequestDto requestDto =
          OrderTestData.buildCreateRequestDto(
              List.of(
                  OrderTestData.buildItemRequestDto(
                      OrderTestData.DEFAULT_ITEM_ID, OrderTestData.UPDATED_QUANTITY),
                  OrderTestData.buildItemRequestDto(
                      OrderTestData.DEFAULT_ITEM_ID, OrderTestData.ONE_QUANTITY)));

      // when & then
      BaseAppException exception =
          assertThrows(BaseAppException.class, () -> orderService.create(userId, requestDto));

      assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);

      verifyNoInteractions(userClientWrapper);
      verifyNoInteractions(orderMapper);
      verifyNoInteractions(itemRepository);
      verifyNoInteractions(orderRepository);
    }
  }

  @Nested
  class GetAllTests {

    @Test
    void whenGetAllWithFilters_thenReturnFilteredPagedOrderResponses() {
      // given
      OrderFilterDto filter =
          OrderTestData.buildOrderFilterDto(
              OrderTestData.MIN_DATE, OrderTestData.MAX_DATE, OrderTestData.TARGET_STATUS);

      Pageable pageable = PageRequest.of(OrderTestData.PAGE_NUMBER, OrderTestData.PAGE_SIZE);

      Order matchingOrder = OrderTestData.buildOrderEntity();
      matchingOrder.setStatus(OrderTestData.TARGET_STATUS);
      matchingOrder.setCreatedAt(OrderTestData.DEFAULT_CREATED_AT);

      Order wrongStatusOrder = OrderTestData.buildOrderEntity();
      wrongStatusOrder.setStatus(OrderTestData.WRONG_STATUS);
      wrongStatusOrder.setCreatedAt(OrderTestData.DEFAULT_CREATED_AT);

      Page<Order> orderPage = new PageImpl<>(List.of(matchingOrder), pageable, 1);

      OrderResponseDto matchingResponseDto = OrderTestData.buildOrderResponseDto();

      when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
      when(orderMapper.toOrderResponseDto(matchingOrder)).thenReturn(matchingResponseDto);

      // when
      Page<OrderResponseDto> result = orderService.getAll(filter, pageable);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getTotalElements()).isEqualTo(1);
      assertThat(result.getContent()).containsExactly(matchingResponseDto);

      verify(orderRepository).findAll(any(Specification.class), eq(pageable));
      verify(orderMapper).toOrderResponseDto(matchingOrder);
      verify(orderMapper, never()).toOrderResponseDto(wrongStatusOrder);
    }
  }

  @Nested
  class GetByIdTests {

    @Test
    void whenGetOrderById_thenOrderIsReturned() {
      // given
      Long orderId = OrderTestData.DEFAULT_ORDER_ID;
      Long userId = OrderTestData.DEFAULT_USER_ID;
      Order order = OrderTestData.buildOrderEntity();
      UserInfoResponseDto userInfo = OrderTestData.buildUserInfoResponseDto();
      OrderWithUserInfoResponseDto expectedResponse =
          OrderTestData.buildOrderWithUserInfoResponseDto();

      when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
      when(userClientWrapper.getUserInfo(userId)).thenReturn(userInfo);
      when(orderMapper.toOrderWithUserInfoResponseDto(order, userInfo))
          .thenReturn(expectedResponse);

      // when
      OrderWithUserInfoResponseDto actualResponse = orderService.getById(orderId);

      // then
      assertThat(actualResponse).isNotNull().isEqualTo(expectedResponse);

      verify(orderRepository).findById(orderId);
      verify(userClientWrapper).getUserInfo(userId);
      verify(orderMapper).toOrderWithUserInfoResponseDto(order, userInfo);
    }

    @Test
    void whenGetOrderById_whenOrderNotFound_thenThrowNotFoundException() {
      // given
      Long nonExistentOrderId = 99999L;

      when(orderRepository.findById(nonExistentOrderId)).thenReturn(Optional.empty());

      // when & then
      BaseAppException exception =
          assertThrows(BaseAppException.class, () -> orderService.getById(nonExistentOrderId));

      assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

      verify(orderRepository).findById(nonExistentOrderId);
      verifyNoInteractions(userClientWrapper);
      verifyNoInteractions(orderMapper);
    }
  }

  @Nested
  class GetByUserIdTests {

    @Test
    void whenGetOrdersByUserId_thenUserOrdersAreReturned() {
      // given
      Long userId = OrderTestData.DEFAULT_USER_ID;
      UserInfoResponseDto userInfo = OrderTestData.buildUserInfoResponseDto();
      List<Order> orders = OrderTestData.buildOrderEntityList();
      UserOrdersResponseDto expectedResponse = OrderTestData.buildUserOrdersResponseDto();

      when(userClientWrapper.getUserInfo(userId)).thenReturn(userInfo);
      when(orderRepository.findAllByUserId(userId)).thenReturn(orders);
      when(orderMapper.toUserOrdersDto(userInfo, orders)).thenReturn(expectedResponse);

      // when
      UserOrdersResponseDto actualResponse = orderService.getByUserId(userId);

      // then
      assertThat(actualResponse).isNotNull().isEqualTo(expectedResponse);

      verify(userClientWrapper).getUserInfo(userId);
      verify(orderRepository).findAllByUserId(userId);
      verify(orderMapper).toUserOrdersDto(userInfo, orders);
    }
  }

  @Nested
  class UpdateTests {

    @Test
    void whenUpdateOrderQuantityOnly_thenOrderIsUpdatedAndReturned() {
      // given
      Item item = OrderTestData.buildItemEntity();
      Order order = OrderTestData.buildOrderEntity();
      OrderItem orderItem =
          OrderTestData.buildOrderItemEntity(
              OrderTestData.DEFAULT_ITEM_ID, order, item, OrderTestData.DEFAULT_QUANTITY);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));

      OrderUpdateRequestDto requestDto =
          OrderTestData.buildUpdateRequestDto(
              List.of(
                  OrderTestData.buildItemRequestDto(
                      OrderTestData.DEFAULT_ITEM_ID, OrderTestData.UPDATED_QUANTITY)));

      UserInfoResponseDto userInfo = OrderTestData.buildUserInfoResponseDto();
      OrderWithUserInfoResponseDto expectedResponse =
          OrderTestData.buildOrderWithUserInfoResponseDto();

      double expectedTotalPrice = OrderTestData.UPDATED_QUANTITY * OrderTestData.DEFAULT_ITEM_PRICE;

      when(orderRepository.findById(OrderTestData.DEFAULT_ORDER_ID)).thenReturn(Optional.of(order));
      when(userClientWrapper.getUserInfo(OrderTestData.DEFAULT_USER_ID)).thenReturn(userInfo);
      when(orderRepository.save(order)).thenReturn(order);
      when(orderMapper.toOrderWithUserInfoResponseDto(order, userInfo))
          .thenReturn(expectedResponse);

      // when
      OrderWithUserInfoResponseDto actualResponse =
          orderService.update(OrderTestData.DEFAULT_ORDER_ID, requestDto);

      // then
      assertThat(actualResponse).isNotNull().isEqualTo(expectedResponse);
      assertThat(order.getTotalPrice()).isEqualTo(expectedTotalPrice);
      assertThat(orderItem.getQuantity()).isEqualTo(OrderTestData.UPDATED_QUANTITY);

      verify(orderRepository).findById(OrderTestData.DEFAULT_ORDER_ID);
      verify(userClientWrapper).getUserInfo(OrderTestData.DEFAULT_USER_ID);
      verify(orderMapper).updateEntity(requestDto, order);
      verify(orderRepository).save(order);
      verify(orderMapper).toOrderWithUserInfoResponseDto(order, userInfo);
      verifyNoInteractions(itemRepository);
    }

    @Test
    void whenUpdateOrderWithExistingAndNewItems_thenOrderIsUpdatedAndReturned() {
      // given
      Long orderId = OrderTestData.DEFAULT_ORDER_ID;
      Long userId = OrderTestData.DEFAULT_USER_ID;

      Item existingItem = OrderTestData.buildItemEntity(OrderTestData.DEFAULT_ITEM_ID);
      existingItem.setPrice(OrderTestData.DEFAULT_ITEM_PRICE);

      Item newItem = OrderTestData.buildItemEntity(OrderTestData.SECOND_ITEM_ID);
      newItem.setPrice(OrderTestData.SECOND_ITEM_PRICE);

      Order existingOrder = OrderTestData.buildOrderEntity();
      existingOrder.setUserId(userId);

      OrderItem orderItem1 =
          OrderTestData.buildOrderItemEntity(
              OrderTestData.DEFAULT_ORDER_ITEM_ID,
              existingOrder,
              existingItem,
              OrderTestData.DEFAULT_QUANTITY);

      existingOrder.setOrderItems(new ArrayList<>(List.of(orderItem1)));

      OrderUpdateRequestDto requestDto =
          OrderTestData.buildUpdateRequestDto(
              orderId,
              List.of(
                  OrderTestData.buildItemRequestDto(
                      OrderTestData.DEFAULT_ITEM_ID, OrderTestData.UPDATED_QUANTITY),
                  OrderTestData.buildItemRequestDto(
                      OrderTestData.SECOND_ITEM_ID, OrderTestData.NEW_ITEM_QUANTITY)));

      UserInfoResponseDto userInfo = OrderTestData.buildUserInfoResponseDto();
      OrderWithUserInfoResponseDto expectedResponse =
          OrderTestData.buildOrderWithUserInfoResponseDto();

      OrderItem mappedNewOrderItem =
          OrderTestData.buildOrderItemEntity(
              OrderTestData.SECOND_ORDER_ITEM_ID,
              existingOrder,
              newItem,
              OrderTestData.NEW_ITEM_QUANTITY);

      double expectedTotalPrice =
          (OrderTestData.UPDATED_QUANTITY * existingItem.getPrice())
              + (OrderTestData.NEW_ITEM_QUANTITY * newItem.getPrice());

      when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
      when(userClientWrapper.getUserInfo(userId)).thenReturn(userInfo);
      when(itemRepository.findAllById(Set.of(OrderTestData.SECOND_ITEM_ID)))
          .thenReturn(List.of(newItem));
      when(orderMapper.toOrderItem(existingOrder, newItem, OrderTestData.NEW_ITEM_QUANTITY))
          .thenReturn(mappedNewOrderItem);
      when(orderRepository.save(existingOrder)).thenReturn(existingOrder);
      when(orderMapper.toOrderWithUserInfoResponseDto(existingOrder, userInfo))
          .thenReturn(expectedResponse);

      // when
      OrderWithUserInfoResponseDto actualResponse = orderService.update(orderId, requestDto);

      // then
      assertThat(actualResponse).isNotNull().isEqualTo(expectedResponse);
      assertThat(existingOrder.getOrderItems()).hasSize(2);
      assertThat(existingOrder.getOrderItems().get(0).getQuantity())
          .isEqualTo(OrderTestData.UPDATED_QUANTITY);
      assertThat(existingOrder.getOrderItems().get(1).getQuantity())
          .isEqualTo(OrderTestData.NEW_ITEM_QUANTITY);
      assertThat(existingOrder.getTotalPrice()).isEqualTo(expectedTotalPrice);

      verify(orderRepository).findById(orderId);
      verify(userClientWrapper).getUserInfo(userId);
      verify(itemRepository).findAllById(Set.of(OrderTestData.SECOND_ITEM_ID));
      verify(orderMapper).toOrderItem(existingOrder, newItem, OrderTestData.NEW_ITEM_QUANTITY);
      verify(orderMapper).updateEntity(eq(requestDto), eq(existingOrder));
      verify(orderRepository).save(existingOrder);
      verify(orderMapper).toOrderWithUserInfoResponseDto(existingOrder, userInfo);
    }
  }

  @Test
  void whenUpdateOrderRemovingOldItem_thenOrderIsUpdatedAndReturned() {
    // given
    Long orderId = OrderTestData.DEFAULT_ORDER_ID;
    Long userId = OrderTestData.DEFAULT_USER_ID;

    Item item1 = OrderTestData.buildItemEntity(OrderTestData.DEFAULT_ITEM_ID);

    Item item2 = OrderTestData.buildItemEntity(OrderTestData.SECOND_ITEM_ID);
    item2.setPrice(OrderTestData.SECOND_ITEM_PRICE);

    Order existingOrder = OrderTestData.buildOrderEntity();
    existingOrder.setUserId(userId);

    OrderItem orderItem1 =
        OrderTestData.buildOrderItemEntity(
            OrderTestData.DEFAULT_ORDER_ITEM_ID,
            existingOrder,
            item1,
            OrderTestData.DEFAULT_QUANTITY);

    OrderItem orderItem2 =
        OrderTestData.buildOrderItemEntity(
            OrderTestData.SECOND_ORDER_ITEM_ID, existingOrder, item2, OrderTestData.ONE_QUANTITY);

    existingOrder.setOrderItems(new ArrayList<>(List.of(orderItem1, orderItem2)));

    OrderUpdateRequestDto requestDto =
        OrderTestData.buildUpdateRequestDto(
            orderId,
            List.of(
                OrderTestData.buildItemRequestDto(
                    OrderTestData.DEFAULT_ITEM_ID, OrderTestData.UPDATED_QUANTITY)));

    UserInfoResponseDto userInfo = OrderTestData.buildUserInfoResponseDto();
    OrderWithUserInfoResponseDto expectedResponse =
        OrderTestData.buildOrderWithUserInfoResponseDto();

    double expectedTotalPrice = OrderTestData.UPDATED_QUANTITY * item1.getPrice();

    when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
    when(userClientWrapper.getUserInfo(userId)).thenReturn(userInfo);
    when(orderRepository.save(existingOrder)).thenReturn(existingOrder);
    when(orderMapper.toOrderWithUserInfoResponseDto(existingOrder, userInfo))
        .thenReturn(expectedResponse);

    // when
    OrderWithUserInfoResponseDto actualResponse = orderService.update(orderId, requestDto);

    // then
    assertThat(actualResponse).isNotNull().isEqualTo(expectedResponse);
    assertThat(existingOrder.getOrderItems()).hasSize(1);
    assertThat(existingOrder.getOrderItems().get(0).getItem().getId())
        .isEqualTo(OrderTestData.DEFAULT_ITEM_ID);
    assertThat(existingOrder.getOrderItems().get(0).getQuantity())
        .isEqualTo(OrderTestData.UPDATED_QUANTITY);
    assertThat(existingOrder.getTotalPrice()).isEqualTo(expectedTotalPrice);

    verify(orderRepository).findById(orderId);
    verify(userClientWrapper).getUserInfo(userId);
    verify(orderMapper).updateEntity(eq(requestDto), eq(existingOrder));
    verify(orderRepository).save(existingOrder);
    verify(orderMapper).toOrderWithUserInfoResponseDto(existingOrder, userInfo);
    verifyNoInteractions(itemRepository);
  }

  @Test
  void whenUpdateOrderWithDuplicateItems_thenThrowConflictException() {
    // given
    Long orderId = OrderTestData.DEFAULT_ORDER_ID;

    OrderUpdateRequestDto requestDto =
        OrderTestData.buildUpdateRequestDto(
            orderId,
            List.of(
                OrderTestData.buildItemRequestDto(
                    OrderTestData.DEFAULT_ITEM_ID, OrderTestData.UPDATED_QUANTITY),
                OrderTestData.buildItemRequestDto(
                    OrderTestData.DEFAULT_ITEM_ID, OrderTestData.ONE_QUANTITY)));

    // when & then
    BaseAppException exception =
        assertThrows(BaseAppException.class, () -> orderService.update(orderId, requestDto));

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);

    verifyNoInteractions(orderRepository);
    verifyNoInteractions(itemRepository);
    verifyNoInteractions(userClientWrapper);
    verifyNoInteractions(orderMapper);
  }

  @Test
  void whenUpdateOrderWithNonExistentOrder_thenThrowNotFoundException() {
    // given
    Long nonExistentOrderId = OrderTestData.NON_EXISTENT_ITEM_ID;
    OrderUpdateRequestDto requestDto =
        OrderTestData.buildUpdateRequestDto(
            nonExistentOrderId,
            List.of(
                OrderTestData.buildItemRequestDto(
                    OrderTestData.DEFAULT_ITEM_ID, OrderTestData.UPDATED_QUANTITY)));

    when(orderRepository.findById(nonExistentOrderId)).thenReturn(Optional.empty());

    // when & then
    BaseAppException exception =
        assertThrows(
            BaseAppException.class, () -> orderService.update(nonExistentOrderId, requestDto));

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

    verify(orderRepository).findById(nonExistentOrderId);
    verifyNoInteractions(itemRepository);
    verifyNoInteractions(userClientWrapper);
    verifyNoInteractions(orderMapper);
  }

  @Test
  void whenUpdateOrderWhenUserNotFound_thenThrowException() {
    // given
    Long orderId = OrderTestData.DEFAULT_ORDER_ID;
    Long userId = OrderTestData.DEFAULT_USER_ID;

    Order existingOrder = OrderTestData.buildOrderEntity();
    existingOrder.setUserId(userId);

    OrderUpdateRequestDto requestDto =
        OrderTestData.buildUpdateRequestDto(
            orderId,
            List.of(
                OrderTestData.buildItemRequestDto(
                    OrderTestData.DEFAULT_ITEM_ID, OrderTestData.UPDATED_QUANTITY)));

    when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
    when(userClientWrapper.getUserInfo(userId))
        .thenThrow(new RuntimeException("User service error"));

    // when & then
    assertThrows(RuntimeException.class, () -> orderService.update(orderId, requestDto));

    verify(orderRepository).findById(orderId);
    verify(userClientWrapper).getUserInfo(userId);
    verifyNoInteractions(orderMapper);
  }

  @Test
  void whenUpdateOrderWithNonExistentItem_thenThrowNotFoundException() {
    // given
    Long orderId = OrderTestData.DEFAULT_ORDER_ID;
    Long userId = OrderTestData.DEFAULT_USER_ID;

    Order existingOrder = OrderTestData.buildOrderEntity();
    existingOrder.setUserId(userId);
    existingOrder.setOrderItems(new ArrayList<>());

    OrderUpdateRequestDto requestDto =
        OrderTestData.buildUpdateRequestDto(
            orderId,
            List.of(
                OrderTestData.buildItemRequestDto(
                    OrderTestData.NON_EXISTENT_ITEM_ID, OrderTestData.DEFAULT_QUANTITY)));

    UserInfoResponseDto userInfo = OrderTestData.buildUserInfoResponseDto();
    Set<Long> expectedItemIds = Set.of(OrderTestData.NON_EXISTENT_ITEM_ID);

    when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));
    when(userClientWrapper.getUserInfo(userId)).thenReturn(userInfo);
    when(itemRepository.findAllById(expectedItemIds)).thenReturn(Collections.emptyList());

    // when & then
    BaseAppException exception =
        assertThrows(BaseAppException.class, () -> orderService.update(orderId, requestDto));

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

    verify(orderRepository).findById(orderId);
    verify(userClientWrapper).getUserInfo(userId);
    verify(itemRepository).findAllById(expectedItemIds);
    verifyNoMoreInteractions(itemRepository);
    verifyNoInteractions(orderMapper);
  }

  @Test
  void whenUpdateOrderWithInvalidStatus_thenThrowBadRequestException() {
    // given
    Long orderId = OrderTestData.DEFAULT_ORDER_ID;
    Long userId = OrderTestData.DEFAULT_USER_ID;

    Order existingOrder = OrderTestData.buildOrderEntity();
    existingOrder.setUserId(userId);
    existingOrder.setStatus(OrderStatus.DONE);

    OrderUpdateRequestDto requestDto =
        OrderTestData.buildUpdateRequestDto(
            orderId,
            List.of(
                OrderTestData.buildItemRequestDto(
                    OrderTestData.DEFAULT_ITEM_ID, OrderTestData.UPDATED_QUANTITY)));

    when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));

    // when & then
    BaseAppException exception =
        assertThrows(BaseAppException.class, () -> orderService.update(orderId, requestDto));

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

    verify(orderRepository).findById(orderId);
    verify(userClientWrapper).getUserInfo(userId);
    verifyNoInteractions(itemRepository);
    verifyNoInteractions(orderMapper);
  }

  @Nested
  class DeleteTests {

    @Test
    void whenDeleteOrder_thenOrderIsDeletedSuccessfully() {
      // given
      Long orderId = OrderTestData.DEFAULT_ORDER_ID;
      Order order = OrderTestData.buildOrderEntity();
      order.setStatus(OrderStatus.PENDING);

      when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

      // when
      orderService.delete(orderId);

      // then
      verify(orderRepository).findById(orderId);
      verify(orderRepository).delete(order);
    }

    @Test
    void whenDeleteNonExistentOrder_thenThrowNotFoundException() {
      // given
      Long orderId = OrderTestData.NON_EXISTENT_ORDER_ID;

      when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

      // when & then
      BaseAppException exception =
          assertThrows(BaseAppException.class, () -> orderService.delete(orderId));

      assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

      verify(orderRepository).findById(orderId);
      verify(orderRepository, never()).delete(any(Order.class));
    }

    @Test
    void whenDeleteOrderWithInvalidStatus_thenThrowBadRequestException() {
      // given
      Long orderId = OrderTestData.DEFAULT_ORDER_ID;
      Order order = OrderTestData.buildOrderEntity();
      order.setStatus(OrderStatus.DONE);

      when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

      // when & then
      BaseAppException exception =
          assertThrows(BaseAppException.class, () -> orderService.delete(orderId));

      assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

      verify(orderRepository).findById(orderId);
      verify(orderRepository, never()).delete(any(Order.class));
    }
  }
}
