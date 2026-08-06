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

  @Override
  @Transactional
  public OrderWithUserInfoResponseDto create(Long userId, OrderCreateRequestDto dto) {
    if (hasDuplicateItems(dto.items())) {
      log.warn("Order creation failed: Item duplicate");
      throw buildConflictException(ITEM_DUPLICATE);
    }

    UserInfoResponseDto userInfo = userClientWrapper.getUserInfo(userId);

    Order order = orderMapper.toOrder(userId);

    List<OrderItem> orderItems = new ArrayList<>();
    double totalPrice = 0.0;

    for (OrderItemRequestDto itemDto : dto.items()) {
      Long itemId = itemDto.itemId();
      Item item =
          itemRepository
              .findById(itemId)
              .orElseThrow(
                  () -> {
                    log.warn("Order creation failed: Item not found | itemId={}", itemId);
                    return buildNotFoundException(ITEM_NOT_FOUND, itemId);
                  });

      OrderItem orderItem = orderMapper.toOrderItem(itemDto, order, item);

      orderItems.add(orderItem);

      totalPrice += item.getPrice() * orderItem.getQuantity();
    }

    order.setOrderItems(orderItems);
    order.setStatus(OrderStatus.NEW);
    order.setTotalPrice(totalPrice);

    Order savedOrder = orderRepository.save(order);
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
    UserInfoResponseDto userInfo = userClientWrapper.getUserInfo(userId);

    List<Order> orders = orderRepository.findAllByUserId(userId);

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

    if (order.getStatus() != OrderStatus.NEW) {
      log.warn(
          "Order update failed: Invalid order status | orderId={}, status={}",
          orderId,
          order.getStatus());
      throw buildBadRequestException(INVALID_ORDER_STATUS, orderId);
    }

    UserInfoResponseDto userInfo = userClientWrapper.getUserInfo(order.getUserId());

    List<OrderItem> currentOrderItems = order.getOrderItems();

    List<OrderItem> itemsToRemove = new ArrayList<>();
    double totalPrice = 0.0;

    Map<Long, Integer> requestedItems =
        dto.items().stream()
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

    for (Map.Entry<Long, Integer> newOrderItem : requestedItems.entrySet()) {
      Long itemId = newOrderItem.getKey();
      Item item =
          itemRepository
              .findById(itemId)
              .orElseThrow(
                  () -> {
                    log.warn("Order update failed: Item not found | itemId={}", itemId);
                    return buildNotFoundException(ITEM_NOT_FOUND, itemId);
                  });
      OrderItem orderItem = orderMapper.toOrderItem(order, item, newOrderItem.getValue());
      currentOrderItems.add(orderItem);
      totalPrice += orderItem.getQuantity() * item.getPrice();
    }

    order.setTotalPrice(totalPrice);

    orderMapper.updateEntity(dto, order);

    Order updatedOrder = orderRepository.save(order);
    log.info("Order updated successfully | orderId={}", updatedOrder.getId());

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
}
