package com.inno.orderservice.repository;

import com.inno.orderservice.entity.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository
    extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
  List<Order> findAllByUserId(Long userId);
}
