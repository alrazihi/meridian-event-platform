package com.meridian.event.infrastructure.web.rest;

import com.meridian.event.application.port.inbound.OrderLineInput;
import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.QueryOrderStatusUseCase;
import com.meridian.event.application.port.outbound.AuthorizationService;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.OrderLine;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.infrastructure.web.dto.PlaceOrderRequest;
import com.meridian.event.infrastructure.web.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlaceOrderUseCase placeOrderUseCase;

    @MockBean
    private QueryOrderStatusUseCase queryOrderStatusUseCase;

    @MockBean
    private AuthorizationService authorizationService;

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldPlaceOrderSuccessfully() throws Exception {
        Order mockOrder = new Order(
                OrderId.from("order-123"), "customer-123",
                List.of(new OrderLine(Sku.of("SKU-1"), 2, Money.of(new BigDecimal("10.00"), "USD")))
        );
        mockOrder.setStatus(com.meridian.event.domain.model.OrderStatus.CONFIRMED);
        when(placeOrderUseCase.placeOrder(anyString(), any(), anyString())).thenReturn(mockOrder);
        when(authorizationService.canAccessOrder(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lines": [
                                        {"sku": "SKU-1", "quantity": 2, "unitPrice": 10.00},
                                        {"sku": "SKU-2", "quantity": 1, "unitPrice": 25.00}
                                    ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.customerId").value("customer-123"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectOrderWithInvalidQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lines": [{"sku": "SKU-1", "quantity": 0, "unitPrice": 10.00}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectOrderWithMissingLines() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lines": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldGetOrderSuccessfully() throws Exception {
        OrderResponse response = new OrderResponse(
                "order-123",
                "customer-123",
                new java.math.BigDecimal("100.00"),
                "CONFIRMED",
                java.time.Instant.now()
        );
        Order mockOrder = new Order(
                OrderId.from("order-123"), "customer-123",
                List.of(new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD")))
        );
        mockOrder.setStatus(com.meridian.event.domain.model.OrderStatus.CONFIRMED);
        when(queryOrderStatusUseCase.getOrderStatus(eq("order-123"), anyString()))
                .thenReturn(mockOrder);

        mockMvc.perform(get("/api/v1/orders/order-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-123"))
                .andExpect(jsonPath("$.customerId").value("customer-123"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(roles = "REVIEWER")
    void shouldAllowReviewerToGetOrder() throws Exception {
        Order mockOrder = new Order(
                OrderId.from("order-123"), "customer-123",
                List.of(new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD")))
        );
        mockOrder.setStatus(com.meridian.event.domain.model.OrderStatus.CONFIRMED);
        when(queryOrderStatusUseCase.getOrderStatus(eq("order-123"), anyString()))
                .thenReturn(mockOrder);

        mockMvc.perform(get("/api/v1/orders/order-123"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/orders/order-123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldReturnNotFoundForNonExistentOrder() throws Exception {
        when(queryOrderStatusUseCase.getOrderStatus(eq("non-existent"), anyString()))
                .thenThrow(new IllegalArgumentException("Order not found"));

        mockMvc.perform(get("/api/v1/orders/non-existent"))
                .andExpect(status().isNotFound());
    }
}
