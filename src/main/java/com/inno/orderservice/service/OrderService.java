package com.inno.orderservice.service;

import com.inno.orderservice.dto.request.OrderCreateRequestDto;
import com.inno.orderservice.dto.request.OrderFilterDto;
import com.inno.orderservice.dto.request.OrderUpdateRequestDto;
import com.inno.orderservice.dto.response.OrderResponseDto;
import com.inno.orderservice.dto.response.OrderWithUserInfoResponseDto;
import com.inno.orderservice.dto.response.UserOrdersResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

  OrderWithUserInfoResponseDto create(Long userId, OrderCreateRequestDto dto);

  OrderWithUserInfoResponseDto getById(Long orderId);

  UserOrdersResponseDto getByUserId(Long userId);

  Page<OrderResponseDto> getAll(OrderFilterDto filter, Pageable pageable);

  OrderWithUserInfoResponseDto update(Long orderId, OrderUpdateRequestDto dto);

  void delete(Long orderId);
}
