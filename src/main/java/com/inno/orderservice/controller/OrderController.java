package com.inno.orderservice.controller;

import com.inno.orderservice.dto.request.OrderCreateRequestDto;
import com.inno.orderservice.dto.request.OrderFilterDto;
import com.inno.orderservice.dto.request.OrderUpdateRequestDto;
import com.inno.orderservice.dto.response.OrderResponseDto;
import com.inno.orderservice.dto.response.OrderWithUserInfoResponseDto;
import com.inno.orderservice.dto.response.UserOrdersResponseDto;
import com.inno.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/v1/orders")
public class OrderController {

  private final OrderService orderService;

  @PostMapping("/{userId}")
  @PreAuthorize("hasRole('USER') and #userId == authentication.principal")
  public ResponseEntity<OrderWithUserInfoResponseDto> createOrder(
      @PathVariable Long userId, @Valid @RequestBody OrderCreateRequestDto request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(userId, request));
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Page<OrderResponseDto>> getAll(
      @Valid OrderFilterDto filter, @PageableDefault(size = 15) Pageable pageable) {
    return ResponseEntity.ok(orderService.getAll(filter, pageable));
  }

  @GetMapping("/{orderId}")
  @PreAuthorize("hasRole('ADMIN') or @orderSecurity.isOwner(#orderId, authentication.principal)")
  public ResponseEntity<OrderWithUserInfoResponseDto> getById(@PathVariable Long orderId) {
    return ResponseEntity.ok(orderService.getById(orderId));
  }

  @GetMapping("/user/{userId}")
  @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal")
  public ResponseEntity<UserOrdersResponseDto> getByUserId(@PathVariable Long userId) {
    return ResponseEntity.ok(orderService.getByUserId(userId));
  }

  @PutMapping("/{orderId}")
  @PreAuthorize("hasRole('USER') and @orderSecurity.isOwner(#orderId, authentication.principal)")
  public ResponseEntity<OrderWithUserInfoResponseDto> update(
      @PathVariable Long orderId, @RequestBody OrderUpdateRequestDto request) {
    return ResponseEntity.ok(orderService.update(orderId, request));
  }

  @DeleteMapping("/{orderId}")
  @PreAuthorize("hasRole('ADMIN') or @orderSecurity.isOwner(#orderId, authentication.principal)")
  public ResponseEntity<Void> delete(@PathVariable Long orderId) {
    orderService.delete(orderId);
    return ResponseEntity.noContent().build();
  }
}
