package com.inno.orderservice.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inno.orderservice.dto.request.OrderCreateRequestDto;
import com.inno.orderservice.entity.Item;
import com.inno.orderservice.exception.ExceptionResponseDTO;
import com.inno.orderservice.repository.ItemRepository;
import com.inno.orderservice.repository.OrderRepository;
import com.inno.orderservice.util.OrderTestData;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@TestPropertySource(
    properties = {
      "resilience4j.circuitbreaker.instances.userServiceClient.minimum-number-of-calls=5",
      "resilience4j.circuitbreaker.instances.userServiceClient.failure-rate-threshold=50.0"
    })
class CircuitBreakerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @Autowired private ItemRepository itemRepository;

  @Autowired private OrderRepository orderRepository;

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

  @Test
  void shouldIgnore404FromUserServiceAndNotCountAsFailure() throws Exception {
    Long userId = OrderTestData.DEFAULT_USER_ID;
    Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
    OrderCreateRequestDto requestDto =
        OrderTestData.buildCreateRequestDto(
            List.of(
                OrderTestData.buildItemRequestDto(item.getId(), OrderTestData.DEFAULT_QUANTITY)));

    ExceptionResponseDTO errorDto =
        ExceptionResponseDTO.of(HttpStatus.NOT_FOUND, "User not found", List.of("Not found"));

    wireMockServer.stubFor(
        get(urlEqualTo(OrderTestData.USERS_API_PATH + userId))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody(objectMapper.writeValueAsString(errorDto))));

    mockMvc.perform(
        post(OrderTestData.ORDERS_PATH_WITH_USER_ID, userId)
            .with(userAuth(userId, OrderTestData.USER))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestDto)));

    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("userServiceClient");

    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
  }

  @Test
  void shouldRecord500FromUserServiceAsFailure() throws Exception {
    Long userId = OrderTestData.DEFAULT_USER_ID;
    Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
    OrderCreateRequestDto requestDto =
        OrderTestData.buildCreateRequestDto(
            List.of(
                OrderTestData.buildItemRequestDto(item.getId(), OrderTestData.DEFAULT_QUANTITY)));

    ExceptionResponseDTO errorDto =
        ExceptionResponseDTO.of(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", List.of("DB dead"));

    wireMockServer.stubFor(
        get(urlEqualTo(OrderTestData.USERS_API_PATH + userId))
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody(objectMapper.writeValueAsString(errorDto))));

    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("userServiceClient");

    for (int i = 0; i < 5; i++) {
      mockMvc.perform(
          post(OrderTestData.ORDERS_PATH_WITH_USER_ID, userId)
              .with(userAuth(userId, OrderTestData.USER))
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(requestDto)));
    }

    assertThat(cb.getMetrics().getNumberOfFailedCalls()).isGreaterThan(0);
  }

  @Test
  void shouldTransitionToOpenStateWhenFailureRateExceeded() throws Exception {
    Long userId = OrderTestData.DEFAULT_USER_ID;
    Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
    OrderCreateRequestDto requestDto =
        OrderTestData.buildCreateRequestDto(
            List.of(
                OrderTestData.buildItemRequestDto(item.getId(), OrderTestData.DEFAULT_QUANTITY)));

    ExceptionResponseDTO errorDto =
        ExceptionResponseDTO.of(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", List.of("DB dead"));

    wireMockServer.stubFor(
        get(urlEqualTo(OrderTestData.USERS_API_PATH + userId))
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody(objectMapper.writeValueAsString(errorDto))));

    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("userServiceClient");

    for (int i = 0; i < 5; i++) {
      mockMvc.perform(
          post(OrderTestData.ORDERS_PATH_WITH_USER_ID, userId)
              .with(userAuth(userId, OrderTestData.USER))
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(requestDto)));
    }

    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
  }

  @Test
  void shouldInvokeFallbackWhenCircuitBreakerIsOpen() throws Exception {
    Long userId = OrderTestData.DEFAULT_USER_ID;
    Item item = itemRepository.save(OrderTestData.buildItemEntityWithoutId());
    OrderCreateRequestDto requestDto =
        OrderTestData.buildCreateRequestDto(
            List.of(
                OrderTestData.buildItemRequestDto(item.getId(), OrderTestData.DEFAULT_QUANTITY)));

    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("userServiceClient");
    cb.transitionToOpenState();

    mockMvc
        .perform(
            post(OrderTestData.ORDERS_PATH_WITH_USER_ID, userId)
                .with(userAuth(userId, OrderTestData.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userInfo.id").value(userId))
        .andExpect(jsonPath("$.userInfo.name").doesNotExist())
        .andExpect(jsonPath("$.userInfo.surname").doesNotExist())
        .andExpect(jsonPath("$.userInfo.email").doesNotExist())
        .andExpect(jsonPath("$.userInfo.active").doesNotExist());
  }
}
