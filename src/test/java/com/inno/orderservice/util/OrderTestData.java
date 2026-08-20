package com.inno.orderservice.util;

import com.inno.orderservice.dto.request.OrderCreateRequestDto;
import com.inno.orderservice.dto.request.OrderFilterDto;
import com.inno.orderservice.dto.request.OrderItemRequestDto;
import com.inno.orderservice.dto.request.OrderUpdateRequestDto;
import com.inno.orderservice.dto.response.OrderItemResponseDto;
import com.inno.orderservice.dto.response.OrderResponseDto;
import com.inno.orderservice.dto.response.OrderWithUserInfoResponseDto;
import com.inno.orderservice.dto.response.UserInfoResponseDto;
import com.inno.orderservice.dto.response.UserOrdersResponseDto;
import com.inno.orderservice.entity.Item;
import com.inno.orderservice.entity.Order;
import com.inno.orderservice.entity.OrderItem;
import com.inno.orderservice.entity.OrderStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OrderTestData {

  // --- API Paths ---
  public static final String ORDERS_API_PATH = "/api/v1/orders";
  public static final String ORDERS_PATH_WITH_USER_ID = "/api/v1/orders/{userId}";
  public static final String ORDERS_PATH_WITH_ORDER_ID = "/api/v1/orders/{orderId}";
  public static final String USERS_API_PATH = "/api/v1/users/";
  public static final String ORDERS_API_BY_USER_ID = "/api/v1/orders/user/{userId}";

  // --- Roles ---
  public static final String USER = "ROLE_USER";
  public static final String ADMIN = "ROLE_ADMIN";

  // --- Test Identifiers & Numeric Values ---
  public static final Long DEFAULT_USER_ID = 1L;
  public static final Long SECOND_USER_ID = 2L;
  public static final Long DEFAULT_ORDER_ID = 1L;
  public static final Long NON_EXISTENT_ORDER_ID = 100L;
  public static final Long DEFAULT_ITEM_ID = 10L;
  public static final Long NON_EXISTENT_ITEM_ID = 99999L;
  public static final Integer DEFAULT_QUANTITY = 2;
  public static final Integer ONE_QUANTITY = 1;
  public static final Double DEFAULT_ITEM_PRICE = 500.0;
  public static final Double DEFAULT_TOTAL_PRICE = 1000.0;
  public static final Long SECOND_ITEM_ID = 20L;
  public static final Double SECOND_ITEM_PRICE = 300.0;
  public static final Integer UPDATED_QUANTITY = 5;
  public static final Integer NEW_ITEM_QUANTITY = 3;
  public static final Long DEFAULT_ORDER_ITEM_ID = 1L;
  public static final Long SECOND_ORDER_ITEM_ID = 2L;
  public static final int PAGE_NUMBER = 0;
  public static final int PAGE_SIZE = 10;

  // --- Text Attributes ---
  public static final String DEFAULT_ITEM_NAME = "Test Item";
  public static final String DEFAULT_USER_NAME = "Alice";
  public static final String DEFAULT_USER_SURNAME = "Britt";
  public static final String DEFAULT_USER_EMAIL = "alice@gmail.com";
  public static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(2000, 1, 23);
  public static final LocalDate MIN_DATE = LocalDate.of(2026, 1, 1);
  public static final LocalDate MAX_DATE = LocalDate.of(2026, 12, 31);
  public static final OrderStatus TARGET_STATUS = OrderStatus.NEW;
  public static final OrderStatus WRONG_STATUS = OrderStatus.DONE;
  public static final OffsetDateTime DEFAULT_CREATED_AT =
      OffsetDateTime.of(2026, 6, 6, 12, 0, 0, 0, ZoneOffset.UTC);

  public static OrderItemRequestDto buildItemRequestDto() {
    return buildItemRequestDto(DEFAULT_ITEM_ID, DEFAULT_QUANTITY);
  }

  public static OrderItemRequestDto buildItemRequestDto(Long itemId, Integer quantity) {
    return OrderItemRequestDto.builder().itemId(itemId).quantity(quantity).build();
  }

  public static OrderFilterDto buildOrderFilterDto(
      LocalDate minDate, LocalDate maxDate, OrderStatus status) {
    return OrderFilterDto.builder().minDate(minDate).maxDate(maxDate).status(status).build();
  }

  public static OrderCreateRequestDto buildCreateRequestDto() {
    return OrderCreateRequestDto.builder().items(List.of(buildItemRequestDto())).build();
  }

  public static OrderCreateRequestDto buildCreateRequestDto(List<OrderItemRequestDto> items) {
    return OrderCreateRequestDto.builder().items(items).build();
  }

  public static OrderUpdateRequestDto buildUpdateRequestDto(List<OrderItemRequestDto> items) {
    return buildUpdateRequestDto(DEFAULT_ORDER_ID, items);
  }

  public static OrderUpdateRequestDto buildUpdateRequestDto(
      Long orderId, List<OrderItemRequestDto> items) {
    return OrderUpdateRequestDto.builder().orderId(orderId).items(items).build();
  }

  public static Item buildItemEntity() {
    return Item.builder()
        .id(DEFAULT_ITEM_ID)
        .name(DEFAULT_ITEM_NAME)
        .price(DEFAULT_ITEM_PRICE)
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
  }

  public static Item buildItemEntityWithoutId() {
    return Item.builder()
        .name(DEFAULT_ITEM_NAME)
        .price(DEFAULT_ITEM_PRICE)
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
  }

  public static Item buildItemEntity(Long itemId) {
    return Item.builder()
        .id(itemId)
        .name(DEFAULT_ITEM_NAME)
        .price(DEFAULT_ITEM_PRICE)
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
  }

  public static Order buildOrderEntity() {
    return Order.builder()
        .id(DEFAULT_ORDER_ID)
        .userId(DEFAULT_USER_ID)
        .status(OrderStatus.NEW)
        .totalPrice(DEFAULT_TOTAL_PRICE)
        .deleted(false)
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
  }

  public static OrderItem buildOrderItemEntity(Order order, Item item) {
    return OrderItem.builder().order(order).item(item).quantity(DEFAULT_QUANTITY).build();
  }

  public static OrderItem buildOrderItemEntity(Long id, Order order, Item item, Integer quantity) {
    return OrderItem.builder().id(id).order(order).item(item).quantity(quantity).build();
  }

  public static UserInfoResponseDto buildUserInfoResponseDto() {
    return UserInfoResponseDto.builder()
        .id(DEFAULT_USER_ID)
        .name(DEFAULT_USER_NAME)
        .surname(DEFAULT_USER_SURNAME)
        .birthDate(DEFAULT_BIRTH_DATE)
        .email(DEFAULT_USER_EMAIL)
        .active(true)
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
  }

  public static OrderItemResponseDto buildItemResponseDto() {
    return OrderItemResponseDto.builder()
        .itemId(DEFAULT_ITEM_ID)
        .quantity(DEFAULT_QUANTITY)
        .build();
  }

  public static OrderResponseDto buildOrderResponseDto() {
    return OrderResponseDto.builder()
        .id(DEFAULT_ORDER_ID)
        .status(OrderStatus.NEW)
        .totalPrice(DEFAULT_TOTAL_PRICE)
        .items(List.of(buildItemResponseDto()))
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
  }

  public static Order buildOrderEntityWithoutId() {
    return Order.builder()
        .userId(DEFAULT_USER_ID)
        .status(OrderStatus.NEW)
        .totalPrice(DEFAULT_TOTAL_PRICE)
        .deleted(false)
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
  }

  public static OrderWithUserInfoResponseDto buildOrderWithUserInfoResponseDto() {
    return OrderWithUserInfoResponseDto.builder()
        .userInfo(buildUserInfoResponseDto())
        .order(buildOrderResponseDto())
        .build();
  }

  public static UserOrdersResponseDto buildUserOrdersResponseDto() {
    return UserOrdersResponseDto.builder()
        .userInfo(buildUserInfoResponseDto())
        .orders(List.of(buildOrderResponseDto()))
        .build();
  }

  public static List<Order> buildOrderEntityList() {
    return List.of(buildOrderEntity());
  }

  public static List<OrderItem> buildOrderItemEntityList(Order order, Item item) {
    return List.of(buildOrderItemEntity(order, item));
  }
}
