package com.inno.orderservice.mapper;

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
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

  Order toOrder(Long userId);

  void updateEntity(OrderUpdateRequestDto dto, @MappingTarget Order entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "quantity", source = "dto.quantity")
  @Mapping(target = "order", source = "order")
  @Mapping(target = "item", source = "item")
  OrderItem toOrderItem(OrderItemRequestDto dto, Order order, Item item);

  @Mapping(target = "id", ignore = true)
  OrderItem toOrderItem(Order order, Item item, Integer quantity);

  @Mapping(target = "itemId", source = "item.id")
  OrderItemResponseDto toItemResponseDto(OrderItem orderItem);

  @Mapping(target = "items", source = "orderItems")
  OrderResponseDto toOrderResponseDto(Order order);

  @Mapping(target = "userInfo", source = "userInfo")
  OrderWithUserInfoResponseDto toOrderWithUserInfoResponseDto(
      Order order, UserInfoResponseDto userInfo);

  @Mapping(target = "userInfo", source = "userInfo")
  @Mapping(target = "orders", source = "orders")
  UserOrdersResponseDto toUserOrdersDto(UserInfoResponseDto userInfo, List<Order> orders);
}
