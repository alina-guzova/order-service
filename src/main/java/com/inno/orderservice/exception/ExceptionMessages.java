package com.inno.orderservice.exception;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ExceptionMessages {
  // Item exceptions
  public static final String ITEM_NOT_FOUND = "Failed to retrieve item with id = %d";
  public static final String ITEM_DUPLICATE = "Duplicate in item list";

  // Order exceptions
  public static final String ORDER_NOT_FOUND = "Failed to retrieve order with id = %d";
  public static final String INVALID_ORDER_STATUS = "Order status must be NEW, order id = %d";
}
