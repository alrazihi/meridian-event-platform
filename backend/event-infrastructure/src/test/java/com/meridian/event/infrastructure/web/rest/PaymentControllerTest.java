package com.meridian.event.infrastructure.web.rest;

import com.meridian.event.application.port.inbound.CompletePaymentUseCase;
import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import com.meridian.event.infrastructure.web.dto.PaymentRequest;
import com.meridian.event.infrastructure.web.dto.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProcessPaymentUseCase processPaymentUseCase;

    @MockBean
    private CompletePaymentUseCase completePaymentUseCase;

    @MockBean
    private SecurityAuditLogger auditLogger;

    @MockBean
    private Executor paymentTaskExecutor;

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldProcessPaymentSuccessfully() throws Exception {
        var payment = new com.meridian.event.domain.model.Payment(
                com.meridian.event.domain.model.valueobjects.PaymentId.from("payment-123"),
                "order-123",
                com.meridian.event.domain.model.valueobjects.Money.of(new java.math.BigDecimal("100.00"), "USD"),
                "CREDIT_CARD"
        );
        payment.setStatus(com.meridian.event.domain.model.PaymentStatus.PENDING);

        when(processPaymentUseCase.processPayment(anyString(), anyDouble(), anyString(), anyString()))
                .thenReturn(payment);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "orderId": "order-123",
                                    "amount": 100.00,
                                    "paymentMethod": "CREDIT_CARD"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value("payment-123"))
                .andExpect(jsonPath("$.orderId").value("order-123"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentMethod").value("CREDIT_CARD"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectPaymentWithMissingOrderId() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "amount": 100.00,
                                    "paymentMethod": "CREDIT_CARD"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectPaymentWithNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "orderId": "order-123",
                                    "amount": -50.00,
                                    "paymentMethod": "CREDIT_CARD"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldAcceptPaymentCompletion() throws Exception {
        mockMvc.perform(post("/api/v1/payments/payment-123/complete"))
                .andExpect(status().isAccepted());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(post("/api/v1/payments"))
                .andExpect(status().isUnauthorized());
    }
}
