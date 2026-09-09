package com.meridian.event.infrastructure.web.rest;

import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.QueryOrderStatusUseCase;
import com.meridian.event.application.port.outbound.AuthorizationService;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import com.meridian.event.infrastructure.web.dto.PlaceOrderRequest;
import com.meridian.event.infrastructure.web.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(OrderController.class)
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlaceOrderUseCase placeOrderUseCase;

    @MockBean
    private QueryOrderStatusUseCase queryOrderStatusUseCase;

    @MockBean
    private SecurityAuditLogger auditLogger;

    @MockBean
    private AuthorizationService authorizationService;

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldAllowOperatorToPlaceOrderForSelf() throws Exception {
        String customerId = "customer-123";
        Jwt jwt = createJwtWithCustomerId(customerId);

        when(placeOrderUseCase.placeOrder(eq(customerId), any(), eq(customerId)))
                .thenReturn(createMockOrder(customerId, "order-123"));
        when(authorizationService.canAccessOrder(eq(customerId), eq(customerId))).thenReturn(true);

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lines": [{"sku": "SKU-1", "quantity": 1, "unitPrice": 10.00}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldDenyOperatorPlacingOrderForAnotherCustomer() throws Exception {
        String authenticatedCustomer = "customer-123";
        String targetCustomer = "customer-456";
        Jwt jwt = createJwtWithCustomerId(authenticatedCustomer);

        when(authorizationService.canAccessOrder(eq(authenticatedCustomer), eq(targetCustomer))).thenReturn(false);

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lines": [{"sku": "SKU-1", "quantity": 1, "unitPrice": 10.00}]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldAllowAccessToOwnOrder() throws Exception {
        String customerId = "customer-123";
        String orderId = "order-123";
        Jwt jwt = createJwtWithCustomerId(customerId);

        when(queryOrderStatusUseCase.getOrderStatus(eq(orderId), eq(customerId)))
                .thenReturn(createMockOrder(customerId, orderId));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .with(jwt().jwt(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.customerId").value(customerId));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldDenyAccessToAnotherCustomersOrder() throws Exception {
        String authenticatedCustomer = "customer-123";
        String otherCustomer = "customer-456";
        String orderId = "order-123";
        Jwt jwt = createJwtWithCustomerId(authenticatedCustomer);

        when(queryOrderStatusUseCase.getOrderStatus(eq(orderId), eq(authenticatedCustomer)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .with(jwt().jwt(jwt)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToAccessAnyOrder() throws Exception {
        String adminCustomerId = "admin-123";
        String orderId = "order-456";
        String orderCustomerId = "customer-789";
        Jwt jwt = createJwtWithCustomerId(adminCustomerId);

        when(queryOrderStatusUseCase.getOrderStatus(eq(orderId), eq(adminCustomerId)))
                .thenReturn(createMockOrder(orderCustomerId, orderId));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .with(jwt().jwt(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(orderCustomerId));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldDenyUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/orders/order-123"))
                .andExpect(status().isUnauthorized());
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
