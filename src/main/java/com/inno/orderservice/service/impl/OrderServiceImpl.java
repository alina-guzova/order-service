package com.inno.orderservice.service.impl;

import static com.inno.orderservice.exception.BaseAppExceptionBuilder.buildBadRequestException;
import static com.inno.orderservice.exception.BaseAppExceptionBuilder.buildConflictException;
import static com.inno.orderservice.exception.BaseAppExceptionBuilder.buildNotFoundException;
import static com.inno.orderservice.exception.ExceptionMessages.INVALID_ORDER_STATUS;
import static com.inno.orderservice.exception.ExceptionMessages.ITEM_DUPLICATE;
import static com.inno.orderservice.exception.ExceptionMessages.ITEM_NOT_FOUND;
import static com.inno.orderservice.exception.ExceptionMessages.ORDER_NOT_FOUND;

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
import com.inno.orderservice.integration.UserClientWrapper;
import com.inno.orderservice.mapper.OrderMapper;
import com.inno.orderservice.repository.ItemRepository;
import com.inno.orderservice.repository.OrderRepository;
import com.inno.orderservice.repository.specification.OrderSpecifications;
import com.inno.orderservice.service.OrderService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderServiceImpl implements OrderService {

  private final OrderRepository orderRepository;
  private final ItemRepository itemRepository;
  private final OrderMapper orderMapper;
  private final UserClientWrapper userClientWrapper;
  private final Executor orderAsyncPool;

  @Override
  @Transactional
  public OrderWithUserInfoResponseDto create(Long userId, OrderCreateRequestDto dto) {
    if (hasDuplicateItems(dto.items())) {
      log.warn("Order creation failed: Item duplicate");
      throw buildConflictException(ITEM_DUPLICATE);
    }

    CompletableFuture<UserInfoResponseDto> userInfoFuture = requestUserInfoAsync(userId);

    Map<Long, Item> itemMap = getValidatedItemMap(dto.items());
    List<OrderItem> orderItems = createOrderItems(dto.items(), itemMap);
    double totalPrice = calculateTotalPrice(orderItems);

    Order order = orderMapper.toOrder(userId, orderItems, totalPrice, OrderStatus.NEW);
    orderItems.forEach(item -> item.setOrder(order));

    Order savedOrder = orderRepository.save(order);

    UserInfoResponseDto userInfo = userInfoFuture.join();

    log.info("Order created successfully | orderId={}", savedOrder.getId());
    return orderMapper.toOrderWithUserInfoResponseDto(savedOrder, userInfo);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<OrderResponseDto> getAll(OrderFilterDto filter, Pageable pageable) {
    Specification<Order> spec =
        Specification.where(
                OrderSpecifications.creationDateBetween(filter.minDate(), filter.maxDate()))
            .and(OrderSpecifications.hasStatus(filter.status()));

    return orderRepository.findAll(spec, pageable).map(orderMapper::toOrderResponseDto);
  }

  @Override
  @Transactional(readOnly = true)
  public OrderWithUserInfoResponseDto getById(Long orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(
                () -> {
                  log.warn("Order fetch failed: Order not found | itemId={}", orderId);
                  return buildNotFoundException(ORDER_NOT_FOUND, orderId);
                });
    UserInfoResponseDto userInfo = userClientWrapper.getUserInfo(order.getUserId());

    return orderMapper.toOrderWithUserInfoResponseDto(order, userInfo);
  }

  @Override
  @Transactional(readOnly = true)
  public UserOrdersResponseDto getByUserId(Long userId) {
    CompletableFuture<UserInfoResponseDto> userInfoFuture = requestUserInfoAsync(userId);

    List<Order> orders = orderRepository.findAllByUserId(userId);

    UserInfoResponseDto userInfo = userInfoFuture.join();

    return orderMapper.toUserOrdersDto(userInfo, orders);
  }

  @Override
  @Transactional
  public OrderWithUserInfoResponseDto update(Long orderId, OrderUpdateRequestDto dto) {
    if (hasDuplicateItems(dto.items())) {
      log.warn("Order update failed: Item duplicate | orderId={}", orderId);
      throw buildConflictException(ITEM_DUPLICATE, orderId);
    }

    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(
                () -> {
                  log.warn("Order update failed: Order not found | itemId={}", orderId);
                  return buildNotFoundException(ORDER_NOT_FOUND, orderId);
                });

    CompletableFuture<UserInfoResponseDto> userInfoFuture = requestUserInfoAsync(order.getUserId());

    if (order.getStatus() != OrderStatus.NEW) {
      log.warn(
          "Order update failed: Invalid order status | orderId={}, status={}",
          orderId,
          order.getStatus());
      throw buildBadRequestException(INVALID_ORDER_STATUS, orderId);
    }

    double totalPrice = updateOrderItems(order, dto.items());
    order.setTotalPrice(totalPrice);

    orderMapper.updateEntity(dto, order);

    Order updatedOrder = orderRepository.save(order);
    log.info("Order updated successfully | orderId={}", updatedOrder.getId());

    UserInfoResponseDto userInfo = userInfoFuture.join();

    return orderMapper.toOrderWithUserInfoResponseDto(order, userInfo);
  }

  @Override
  @Transactional
  public void delete(Long orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(
                () -> {
                  log.warn("Order delete failed: Order not found | orderId={}", orderId);
                  return buildNotFoundException(ORDER_NOT_FOUND, orderId);
                });

    if (order.getStatus() != OrderStatus.NEW) {
      log.warn(
          "Order delete failed: Invalid order status | orderId={}, status={}",
          orderId,
          order.getStatus());
      throw buildBadRequestException(INVALID_ORDER_STATUS, orderId);
    }

    orderRepository.delete(order);
  }

  private boolean hasDuplicateItems(List<OrderItemRequestDto> items) {
    Set<Long> uniqueItems = new HashSet<>();
    for (OrderItemRequestDto item : items) {
      if (!uniqueItems.add(item.itemId())) {
        return true;
      }
    }
    return false;
  }

  private Long findMissingItemId(Set<Long> requestedIds, List<Item> foundItems) {
    Set<Long> foundIds = foundItems.stream().map(Item::getId).collect(Collectors.toSet());

    return requestedIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
  }

  private Map<Long, Item> getValidatedItemMap(List<OrderItemRequestDto> itemDtos) {
    Set<Long> itemIds =
        itemDtos.stream().map(OrderItemRequestDto::itemId).collect(Collectors.toSet());
    return getValidatedItemMap(itemIds);
  }

  private List<OrderItem> createOrderItems(
      List<OrderItemRequestDto> itemDtos, Map<Long, Item> itemMap) {
    List<OrderItem> orderItems = new ArrayList<>();
    for (OrderItemRequestDto itemDto : itemDtos) {
      Item item = itemMap.get(itemDto.itemId());
      orderItems.add(orderMapper.toOrderItem(itemDto, item));
    }
    return orderItems;
  }

  private double calculateTotalPrice(List<OrderItem> orderItems) {
    return orderItems.stream()
        .mapToDouble(item -> item.getItem().getPrice() * item.getQuantity())
        .sum();
  }

  private Map<Long, Item> getValidatedItemMap(Set<Long> itemIds) {
    List<Item> foundItems = itemRepository.findAllById(itemIds);

    if (foundItems.size() != itemIds.size()) {
      Long missingItem = findMissingItemId(itemIds, foundItems);
      log.warn("Order creation failed: Item not found | itemId={}", missingItem);
      throw buildNotFoundException(ITEM_NOT_FOUND, missingItem);
    }

    return foundItems.stream().collect(Collectors.toMap(Item::getId, Function.identity()));
  }

  private double updateOrderItems(Order order, List<OrderItemRequestDto> itemDtos) {
    List<OrderItem> currentOrderItems = order.getOrderItems();
    List<OrderItem> itemsToRemove = new ArrayList<>();
    double totalPrice = 0.0;

    Map<Long, Integer> requestedItems =
        itemDtos.stream()
            .collect(Collectors.toMap(OrderItemRequestDto::itemId, OrderItemRequestDto::quantity));

    for (OrderItem existingOrderItem : currentOrderItems) {
      Long itemId = existingOrderItem.getItem().getId();

      if (requestedItems.containsKey(itemId)) {
        existingOrderItem.setQuantity(requestedItems.get(itemId));
        totalPrice += existingOrderItem.getQuantity() * existingOrderItem.getItem().getPrice();
        requestedItems.remove(itemId);
      } else {
        itemsToRemove.add(existingOrderItem);
      }
    }

    currentOrderItems.removeAll(itemsToRemove);

    if (!requestedItems.isEmpty()) {
      Map<Long, Item> newItemMap = getValidatedItemMap(requestedItems.keySet());

      for (Map.Entry<Long, Integer> entry : requestedItems.entrySet()) {
        Long itemId = entry.getKey();
        Integer quantity = entry.getValue();

        Item item = newItemMap.get(itemId);
        OrderItem orderItem = orderMapper.toOrderItem(order, item, quantity);

        currentOrderItems.add(orderItem);
        totalPrice += orderItem.getQuantity() * item.getPrice();
      }
    }

    return totalPrice;
  }

  private CompletableFuture<UserInfoResponseDto> requestUserInfoAsync(Long userId) {
    return CompletableFuture.supplyAsync(
        () -> userClientWrapper.getUserInfo(userId), orderAsyncPool);
  }
}
