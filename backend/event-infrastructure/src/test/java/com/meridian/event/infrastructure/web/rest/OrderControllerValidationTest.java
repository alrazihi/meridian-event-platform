package com.meridian.event.infrastructure.web.rest;

import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.QueryOrderStatusUseCase;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import com.meridian.event.infrastructure.web.dto.PlaceOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(OrderController.class)
class OrderControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlaceOrderUseCase placeOrderUseCase;

    @MockBean
    private QueryOrderStatusUseCase queryOrderStatusUseCase;

    @MockBean
    private SecurityAuditLogger auditLogger;

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectMissingCustomerId() throws Exception {
        Jwt jwt = createJwtWithCustomerId("customer-123");

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lines": [{"sku": "SKU-1", "quantity": 1, "unitPrice": 10.00}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Customer ID is required"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectEmptyLines() throws Exception {
        Jwt jwt = createJwtWithCustomerId("customer-123");

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "customer-123",
                                    "lines": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("At least one order line is required"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectZeroQuantity() throws Exception {
        Jwt jwt = createJwtWithCustomerId("customer-123");

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "customer-123",
                                    "lines": [{"sku": "SKU-1", "quantity": 0, "unitPrice": 10.00}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quantity must be at least 1"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectNegativeQuantity() throws Exception {
        Jwt jwt = createJwtWithCustomerId("customer-123");

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "customer-123",
                                    "lines": [{"sku": "SKU-1", "quantity": -1, "unitPrice": 10.00}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quantity must be at least 1"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectZeroUnitPrice() throws Exception {
        Jwt jwt = createJwtWithCustomerId("customer-123");

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "customer-123",
                                    "lines": [{"sku": "SKU-1", "quantity": 1, "unitPrice": 0.00}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unit price must be at least 0.01"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectNegativeUnitPrice() throws Exception {
        Jwt jwt = createJwtWithCustomerId("customer-123");

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "customer-123",
                                    "lines": [{"sku": "SKU-1", "quantity": 1, "unitPrice": -5.00}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unit price must be at least 0.01"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectMissingSku() throws Exception {
        Jwt jwt = createJwtWithCustomerId("customer-123");

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "customer-123",
                                    "lines": [{"quantity": 1, "unitPrice": 10.00}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SKU is required"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectBlankSku() throws Exception {
        Jwt jwt = createJwtWithCustomerId("customer-123");

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "customer-123",
                                    "lines": [{"sku": "  ", "quantity": 1, "unitPrice": 10.00}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SKU is required"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldAcceptValidOrder() throws Exception {
        Jwt jwt = createJwtWithCustomerId("customer-123");

        when(placeOrderUseCase.placeOrder(any(), any(), anyString()))
                .thenReturn(createMockOrder("customer-123", "order-123"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lines": [{"sku": "SKU-1", "quantity": 2, "unitPrice": 10.00}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("customer-123"));
    }

    private Jwt createJwtWithCustomerId(String customerId) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", customerId)
                .claim("customer_id", customerId)
                .claim("roles", List.of("OPERATOR"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private com.meridian.event.domain.model.Order createMockOrder(String customerId, String orderId) {
        com.meridian.event.domain.model.Order order = new com.meridian.event.domain.model.Order(
                com.meridian.event.domain.model.valueobjects.OrderId.from(orderId),
                customerId,
                List.of(new com.meridian.event.domain.model.OrderLine(
                        com.meridian.event.domain.model.valueobjects.Sku.of("SKU-1"),
                        1,
                        com.meridian.event.domain.model.valueobjects.Money.of(new java.math.BigDecimal("10.00"), "USD")
                ))
        );
        order.setStatus(com.meridian.event.domain.model.OrderStatus.CONFIRMED);
        return order;
    }
}
