package com.inno.orderservice.repository.specification;

import com.inno.orderservice.entity.Order;
import com.inno.orderservice.entity.OrderStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.data.jpa.domain.Specification;

public final class OrderSpecifications {

  public static Specification<Order> creationDateBetween(LocalDate minDate, LocalDate maxDate) {
    return (root, query, cb) -> {
      if (minDate == null && maxDate == null) {
        return null;
      }

      OffsetDateTime startDateTime =
          minDate != null ? minDate.atStartOfDay().atOffset(ZoneOffset.UTC) : null;

      OffsetDateTime endDateTime =
          maxDate != null ? maxDate.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC) : null;

      if (startDateTime != null && endDateTime != null) {
        return cb.between(root.get("createdAt"), startDateTime, endDateTime);
      } else if (startDateTime != null) {
        return cb.greaterThanOrEqualTo(root.get("createdAt"), startDateTime);
      } else {
        return cb.lessThanOrEqualTo(root.get("createdAt"), endDateTime);
      }
    };
  }

  public static Specification<Order> hasStatus(OrderStatus status) {
    return (root, query, cb) -> {
      if (status == null) {
        return null;
      }
      return cb.equal(root.get("status"), status);
    };
  }
}
