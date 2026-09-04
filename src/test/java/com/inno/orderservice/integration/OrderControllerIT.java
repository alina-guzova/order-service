package com.inno.orderservice.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.inno.orderservice.util.OrderTestData.ADMIN;
import static com.inno.orderservice.util.OrderTestData.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inno.orderservice.dto.request.OrderCreateRequestDto;
import com.inno.orderservice.dto.request.OrderItemRequestDto;
import com.inno.orderservice.dto.request.OrderUpdateRequestDto;
import com.inno.orderservice.dto.response.UserInfoResponseDto;
import com.inno.orderservice.entity.Item;
import com.inno.orderservice.entity.Order;
import com.inno.orderservice.entity.OrderItem;
import com.inno.orderservice.entity.OrderStatus;
import com.inno.orderservice.repository.ItemRepository;
import com.inno.orderservice.repository.OrderRepository;
import com.inno.orderservice.util.OrderTestData;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
class OrderControllerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private OrderRepository orderRepository;

  @Autowired private ItemRepository itemRepository;

  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @BeforeEach
  void setUp() {
    orderRepository.deleteAll();
    itemRepository.deleteAll();
    circuitBreakerRegistry.circuitBreaker("userServiceClient").reset();
  }

  private RequestPostProcessor userAuth(Long userId, String role) {
    return request -> {
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(
              userId, null, List.of(new SimpleGrantedAuthority(role)));
      context.setAuthentication(auth);
      return securityContext(context).postProcessRequest(request);
    };
  }

  @Nested
  class CreateOrderTests {

    @Test
    @Transactional
    void shouldCreateOrderSuccessfully() throws Exception {
      // given
      Long userId = OrderTestData.DEFAULT_USER_ID;
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());

      OrderItemRequestDto itemRequestDto =
          OrderTestData.buildItemRequestDto(item.getId(), OrderTestData.DEFAULT_QUANTITY);
      OrderCreateRequestDto requestDto =
          OrderTestData.buildCreateRequestDto(List.of(itemRequestDto));

      UserInfoResponseDto userInfoResponseDto = OrderTestData.buildUserInfoResponseDto();

      wireMockServer.stubFor(
          get(urlEqualTo(OrderTestData.USERS_API_PATH + userId))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(objectMapper.writeValueAsString(userInfoResponseDto))));

      // when & then
      mockMvc
          .perform(
              post(OrderTestData.ORDERS_PATH_WITH_USER_ID, userId)
                  .with(userAuth(userId, USER))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(requestDto)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.order.status", is(OrderTestData.TARGET_STATUS.name())))
          .andExpect(jsonPath("$.order.totalPrice", is(OrderTestData.DEFAULT_TOTAL_PRICE)))
          .andExpect(jsonPath("$.order.items", hasSize(1)))
          .andExpect(jsonPath("$.order.items[0].itemId", is(item.getId().intValue())))
          .andExpect(jsonPath("$.order.items[0].quantity", is(OrderTestData.DEFAULT_QUANTITY)))
          .andExpect(jsonPath("$.userInfo.id", is(userId.intValue())))
          .andExpect(jsonPath("$.userInfo.email", is(OrderTestData.DEFAULT_USER_EMAIL)));

      assertThat(orderRepository.count()).isEqualTo(1);

      Order savedOrder = orderRepository.findAll().get(0);
      assertThat(savedOrder.getUserId()).isEqualTo(userId);
      assertThat(savedOrder.getStatus()).isEqualTo(OrderTestData.TARGET_STATUS);
      assertThat(savedOrder.getTotalPrice()).isEqualTo(OrderTestData.DEFAULT_TOTAL_PRICE);
      assertThat(savedOrder.getOrderItems()).hasSize(1);
      assertThat(savedOrder.getOrderItems().get(0).getItem().getId()).isEqualTo(item.getId());
    }
  }

  @Test
  void shouldReturnForbiddenWhenUserIsAdmin() throws Exception {
    // given
    Long userId = OrderTestData.DEFAULT_USER_ID;
    Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());

    OrderItemRequestDto itemRequestDto =
        OrderTestData.buildItemRequestDto(item.getId(), OrderTestData.DEFAULT_QUANTITY);
    OrderCreateRequestDto requestDto = OrderTestData.buildCreateRequestDto(List.of(itemRequestDto));

    // when & then
    mockMvc
        .perform(
            post(OrderTestData.ORDERS_PATH_WITH_USER_ID, userId)
                .with(userAuth(userId, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isForbidden());

    assertThat(orderRepository.count()).isEqualTo(0);
  }

  @Test
  void shouldReturnForbiddenWhenUserIdDoesNotMatchPrincipal() throws Exception {
    // given
    Long principalId = OrderTestData.DEFAULT_USER_ID;
    Long differentUserId = OrderTestData.SECOND_USER_ID;
    Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());

    OrderItemRequestDto itemRequestDto =
        OrderTestData.buildItemRequestDto(item.getId(), OrderTestData.DEFAULT_QUANTITY);
    OrderCreateRequestDto requestDto = OrderTestData.buildCreateRequestDto(List.of(itemRequestDto));

    // when & then
    mockMvc
        .perform(
            post(OrderTestData.ORDERS_PATH_WITH_USER_ID, differentUserId)
                .with(userAuth(principalId, USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isForbidden());

    assertThat(orderRepository.count()).isEqualTo(0);
  }

  @Test
  void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
    // given
    Long userId = OrderTestData.DEFAULT_USER_ID;
    OrderCreateRequestDto requestDto = OrderTestData.buildCreateRequestDto();

    // when & then
    mockMvc
        .perform(
            post(OrderTestData.ORDERS_PATH_WITH_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isUnauthorized());

    assertThat(orderRepository.count()).isEqualTo(0);
  }

  @Nested
  class GetAllOrdersTests {

    @Test
    @Transactional
    void shouldGetAllOrdersSuccessfullyForAdmin() throws Exception {
      // given
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Order order = OrderTestData.buildOrderEntityWithoutId();
      OrderItem orderItem = OrderTestData.buildOrderItemEntity(order, item);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));
      orderRepository.save(order);

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                      OrderTestData.ORDERS_API_PATH)
                  .with(userAuth(OrderTestData.DEFAULT_USER_ID, ADMIN)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content", hasSize(1)))
          .andExpect(jsonPath("$.content[0].id", is(order.getId().intValue())))
          .andExpect(jsonPath("$.content[0].status", is(order.getStatus().name())))
          .andExpect(jsonPath("$.content[0].totalPrice", is(order.getTotalPrice())));
    }

    @Test
    @Transactional
    void shouldFilterOutOrdersWithNonMatchingStatusForAdmin() throws Exception {
      // given
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());

      Order matchingOrder = OrderTestData.buildOrderEntityWithoutId();
      matchingOrder.setStatus(OrderStatus.PENDING);
      OrderItem matchingItem = OrderTestData.buildOrderItemEntity(matchingOrder, item);
      matchingOrder.setOrderItems(new ArrayList<>(List.of(matchingItem)));
      orderRepository.save(matchingOrder);

      Order nonMatchingOrder = OrderTestData.buildOrderEntityWithoutId();
      nonMatchingOrder.setStatus(OrderStatus.DONE);
      OrderItem nonMatchingItem = OrderTestData.buildOrderItemEntity(nonMatchingOrder, item);
      nonMatchingOrder.setOrderItems(new ArrayList<>(List.of(nonMatchingItem)));
      orderRepository.save(nonMatchingOrder);

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                      OrderTestData.ORDERS_API_PATH)
                  .param("status", "NEW")
                  .with(userAuth(OrderTestData.DEFAULT_USER_ID, ADMIN)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content", hasSize(1)))
          .andExpect(jsonPath("$.content[0].id", is(matchingOrder.getId().intValue())))
          .andExpect(jsonPath("$.content[0].status", is("NEW")));
    }

    @Test
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                  OrderTestData.ORDERS_API_PATH))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnForbiddenWhenUserIsNotAdmin() throws Exception {
      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                      OrderTestData.ORDERS_API_PATH)
                  .with(userAuth(OrderTestData.DEFAULT_USER_ID, USER)))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class GetByIdTests {

    @Test
    @Transactional
    void shouldGetOrderByIdSuccessfullyForAdmin() throws Exception {
      // given
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Order order = OrderTestData.buildOrderEntityWithoutId();
      OrderItem orderItem = OrderTestData.buildOrderItemEntity(order, item);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));
      orderRepository.save(order);

      UserInfoResponseDto userInfoResponseDto = OrderTestData.buildUserInfoResponseDto();

      wireMockServer.stubFor(
          get(urlEqualTo(OrderTestData.USERS_API_PATH + order.getUserId()))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(objectMapper.writeValueAsString(userInfoResponseDto))));

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                      OrderTestData.ORDERS_PATH_WITH_ORDER_ID, order.getId())
                  .with(userAuth(OrderTestData.DEFAULT_USER_ID, ADMIN)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.order.id", is(order.getId().intValue())))
          .andExpect(jsonPath("$.order.status", is(order.getStatus().name())))
          .andExpect(jsonPath("$.userInfo.id", is(userInfoResponseDto.id().intValue())))
          .andExpect(jsonPath("$.userInfo.email", is(userInfoResponseDto.email())));
    }

    @Test
    @Transactional
    void shouldGetOrderByIdSuccessfullyForOwner() throws Exception {
      // given
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Order order = OrderTestData.buildOrderEntityWithoutId();
      order.setUserId(2L);
      OrderItem orderItem = OrderTestData.buildOrderItemEntity(order, item);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));
      orderRepository.save(order);

      UserInfoResponseDto userInfoResponseDto = OrderTestData.buildUserInfoResponseDto();

      wireMockServer.stubFor(
          get(urlEqualTo(OrderTestData.USERS_API_PATH + order.getUserId()))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(objectMapper.writeValueAsString(userInfoResponseDto))));

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                      OrderTestData.ORDERS_PATH_WITH_ORDER_ID, order.getId())
                  .with(userAuth(order.getUserId(), USER)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.order.id", is(order.getId().intValue())))
          .andExpect(jsonPath("$.order.status", is(order.getStatus().name())))
          .andExpect(jsonPath("$.userInfo.id", is(userInfoResponseDto.id().intValue())));
    }

    @Test
    @Transactional
    void shouldReturnForbiddenWhenGetOrderByIdByAnotherUser() throws Exception {
      // given
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Order order = OrderTestData.buildOrderEntityWithoutId();
      order.setUserId(OrderTestData.DEFAULT_USER_ID);
      OrderItem orderItem = OrderTestData.buildOrderItemEntity(order, item);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));
      orderRepository.save(order);

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                      OrderTestData.ORDERS_PATH_WITH_ORDER_ID, order.getId())
                  .with(userAuth(OrderTestData.SECOND_USER_ID, USER)))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class GetByUserIdTests {

    @Test
    @Transactional
    void shouldGetOrdersByUserIdSuccessfullyForAdmin() throws Exception {
      // given
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Order order = OrderTestData.buildOrderEntityWithoutId();
      order.setUserId(OrderTestData.DEFAULT_USER_ID);
      OrderItem orderItem = OrderTestData.buildOrderItemEntity(order, item);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));
      orderRepository.save(order);

      UserInfoResponseDto userInfoResponseDto = OrderTestData.buildUserInfoResponseDto();

      wireMockServer.stubFor(
          get(urlEqualTo(OrderTestData.USERS_API_PATH + OrderTestData.DEFAULT_USER_ID))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(objectMapper.writeValueAsString(userInfoResponseDto))));

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                      OrderTestData.ORDERS_API_BY_USER_ID, OrderTestData.DEFAULT_USER_ID)
                  .with(userAuth(OrderTestData.SECOND_USER_ID, ADMIN)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userInfo.id", is(userInfoResponseDto.id().intValue())))
          .andExpect(jsonPath("$.orders", hasSize(1)))
          .andExpect(jsonPath("$.orders[0].id", is(order.getId().intValue())));
    }

    @Test
    @Transactional
    void shouldGetOrdersByUserIdSuccessfullyForOwner() throws Exception {
      // given
      Long targetUserId = OrderTestData.DEFAULT_USER_ID;

      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Order order = OrderTestData.buildOrderEntityWithoutId();
      order.setUserId(targetUserId);
      OrderItem orderItem = OrderTestData.buildOrderItemEntity(order, item);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));
      orderRepository.save(order);

      UserInfoResponseDto userInfoResponseDto = OrderTestData.buildUserInfoResponseDto();

      wireMockServer.stubFor(
          get(urlEqualTo(OrderTestData.USERS_API_PATH + targetUserId))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(objectMapper.writeValueAsString(userInfoResponseDto))));

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                      OrderTestData.ORDERS_API_BY_USER_ID, targetUserId)
                  .with(userAuth(targetUserId, USER)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userInfo.id", is(userInfoResponseDto.id().intValue())))
          .andExpect(jsonPath("$.orders", hasSize(1)))
          .andExpect(jsonPath("$.orders[0].id", is(order.getId().intValue())));
    }

    @Test
    void shouldReturnUnauthorizedWhenGetOrdersByUserIdWithoutAuthentication() throws Exception {
      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                  OrderTestData.ORDERS_API_BY_USER_ID, OrderTestData.DEFAULT_USER_ID))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnForbiddenWhenGetOrdersByUserIdByAnotherUser() throws Exception {
      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                      OrderTestData.ORDERS_API_BY_USER_ID, OrderTestData.DEFAULT_USER_ID)
                  .with(userAuth(OrderTestData.SECOND_USER_ID, USER)))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class UpdateTests {

    @Test
    @Transactional
    void shouldUpdateOrderSuccessfully() throws Exception {
      // given
      Item item1 = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Item item2 = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Item item3 = itemRepository.save(OrderTestData.buildItemEntityWithoutId());

      Order order = OrderTestData.buildOrderEntityWithoutId();
      order.setUserId(OrderTestData.DEFAULT_USER_ID);
      order.setStatus(OrderTestData.TARGET_STATUS);

      OrderItem orderItem1 = OrderTestData.buildOrderItemEntity(order, item1);
      orderItem1.setQuantity(OrderTestData.ONE_QUANTITY);
      OrderItem orderItem2 = OrderTestData.buildOrderItemEntity(order, item2);
      orderItem2.setQuantity(OrderTestData.ONE_QUANTITY);

      order.setOrderItems(new ArrayList<>(List.of(orderItem1, orderItem2)));
      order.setTotalPrice(item1.getPrice() + item2.getPrice());
      orderRepository.save(order);

      OrderUpdateRequestDto requestDto =
          OrderUpdateRequestDto.builder()
              .orderId(order.getId())
              .items(
                  List.of(
                      OrderItemRequestDto.builder()
                          .itemId(item1.getId())
                          .quantity(OrderTestData.UPDATED_QUANTITY)
                          .build(),
                      OrderItemRequestDto.builder()
                          .itemId(item3.getId())
                          .quantity(OrderTestData.NEW_ITEM_QUANTITY)
                          .build()))
              .build();

      UserInfoResponseDto userInfoResponseDto = OrderTestData.buildUserInfoResponseDto();

      wireMockServer.stubFor(
          get(urlEqualTo(OrderTestData.USERS_API_PATH + order.getUserId()))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(objectMapper.writeValueAsString(userInfoResponseDto))));

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                      OrderTestData.ORDERS_PATH_WITH_ORDER_ID, order.getId())
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(requestDto))
                  .with(userAuth(order.getUserId(), USER)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.order.id", is(order.getId().intValue())))
          .andExpect(
              jsonPath(
                  "$.order.totalPrice",
                  is(
                      OrderTestData.UPDATED_QUANTITY * item1.getPrice()
                          + OrderTestData.NEW_ITEM_QUANTITY * item3.getPrice())))
          .andExpect(jsonPath("$.order.items", hasSize(2)))
          .andExpect(jsonPath("$.userInfo.id", is(userInfoResponseDto.id().intValue())));
    }

    @Test
    @Transactional
    void shouldReturnForbiddenWhenUpdateOrderAsAdmin() throws Exception {
      // given
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Order order = OrderTestData.buildOrderEntityWithoutId();
      order.setUserId(OrderTestData.DEFAULT_USER_ID);
      order.setStatus(OrderTestData.TARGET_STATUS);
      OrderItem orderItem = OrderTestData.buildOrderItemEntity(order, item);
      orderItem.setQuantity(OrderTestData.ONE_QUANTITY);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));
      orderRepository.save(order);

      OrderUpdateRequestDto requestDto =
          OrderUpdateRequestDto.builder()
              .orderId(order.getId())
              .items(
                  List.of(
                      OrderItemRequestDto.builder()
                          .itemId(item.getId())
                          .quantity(OrderTestData.UPDATED_QUANTITY)
                          .build()))
              .build();

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                      OrderTestData.ORDERS_PATH_WITH_ORDER_ID, order.getId())
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(requestDto))
                  .with(userAuth(999L, ADMIN)))
          .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void shouldReturnForbiddenWhenUpdateOrderByAnotherUser() throws Exception {
      // given
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Order order = OrderTestData.buildOrderEntityWithoutId();
      order.setUserId(OrderTestData.DEFAULT_USER_ID);
      order.setStatus(OrderTestData.TARGET_STATUS);
      OrderItem orderItem = OrderTestData.buildOrderItemEntity(order, item);
      orderItem.setQuantity(OrderTestData.ONE_QUANTITY);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));
      orderRepository.save(order);

      OrderUpdateRequestDto requestDto =
          OrderUpdateRequestDto.builder()
              .orderId(order.getId())
              .items(
                  List.of(
                      OrderItemRequestDto.builder()
                          .itemId(item.getId())
                          .quantity(OrderTestData.UPDATED_QUANTITY)
                          .build()))
              .build();

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                      OrderTestData.ORDERS_PATH_WITH_ORDER_ID, order.getId())
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(requestDto))
                  .with(userAuth(OrderTestData.SECOND_USER_ID, USER)))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class DeleteTests {

    @Test
    @Transactional
    void shouldDeleteOrderSuccessfullyForAdmin() throws Exception {
      // given
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Order order = OrderTestData.buildOrderEntityWithoutId();
      order.setUserId(OrderTestData.DEFAULT_USER_ID);
      order.setStatus(OrderTestData.TARGET_STATUS);
      OrderItem orderItem = OrderTestData.buildOrderItemEntity(order, item);
      orderItem.setQuantity(OrderTestData.ONE_QUANTITY);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));
      orderRepository.save(order);

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                      OrderTestData.ORDERS_PATH_WITH_ORDER_ID, order.getId())
                  .with(userAuth(OrderTestData.SECOND_USER_ID, ADMIN)))
          .andExpect(status().isNoContent());
    }

    @Test
    @Transactional
    void shouldDeleteOrderSuccessfullyForOwner() throws Exception {
      // given
      Long ownerId = OrderTestData.DEFAULT_USER_ID;
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Order order = OrderTestData.buildOrderEntityWithoutId();
      order.setUserId(ownerId);
      order.setStatus(OrderTestData.TARGET_STATUS);
      OrderItem orderItem = OrderTestData.buildOrderItemEntity(order, item);
      orderItem.setQuantity(OrderTestData.ONE_QUANTITY);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));
      orderRepository.save(order);

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                      OrderTestData.ORDERS_PATH_WITH_ORDER_ID, order.getId())
                  .with(userAuth(ownerId, USER)))
          .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnUnauthorizedWhenDeleteOrderWithoutAuthentication() throws Exception {
      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                  OrderTestData.ORDERS_PATH_WITH_ORDER_ID, OrderTestData.DEFAULT_ORDER_ID))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void shouldReturnForbiddenWhenDeleteOrderByAnotherUser() throws Exception {
      // given
      Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
      Order order = OrderTestData.buildOrderEntityWithoutId();
      order.setUserId(OrderTestData.DEFAULT_USER_ID);
      order.setStatus(OrderTestData.TARGET_STATUS);
      OrderItem orderItem = OrderTestData.buildOrderItemEntity(order, item);
      orderItem.setQuantity(OrderTestData.ONE_QUANTITY);
      order.setOrderItems(new ArrayList<>(List.of(orderItem)));
      orderRepository.save(order);

      // when & then
      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                      OrderTestData.ORDERS_PATH_WITH_ORDER_ID, order.getId())
                  .with(userAuth(OrderTestData.SECOND_USER_ID, USER)))
          .andExpect(status().isForbidden());
    }
  }
}
