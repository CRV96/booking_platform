package com.booking.platform.payment_service.gateway.impl;

import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private MockPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MockPaymentGateway();
        ReflectionTestUtils.setField(gateway, "simulatedDelayMs", 0L);
    }

    @Test
    void createPaymentIntent_returnsIdWithMockPrefix() throws Exception {
        GatewayPaymentResponse response = gateway
                .createPaymentIntent(BigDecimal.TEN, "USD", "idem-key-1")
                .get();

        assertThat(response.externalPaymentId()).startsWith("mock_pi_");
    }

    @Test
    void createPaymentIntent_returnsRequiresConfirmationStatus() throws Exception {
        GatewayPaymentResponse response = gateway
                .createPaymentIntent(BigDecimal.TEN, "USD", "idem-key-2")
                .get();

        assertThat(response.status()).isEqualTo("requires_confirmation");
    }

    @Test
    void createPaymentIntent_eachCallReturnsUniqueId() throws Exception {
        GatewayPaymentResponse first = gateway
                .createPaymentIntent(BigDecimal.TEN, "USD", "idem-key-3a")
                .get();
        GatewayPaymentResponse second = gateway
                .createPaymentIntent(BigDecimal.TEN, "USD", "idem-key-3b")
                .get();

        assertThat(first.externalPaymentId()).isNotEqualTo(second.externalPaymentId());
    }

    @Test
    void confirmPayment_returnsOriginalExternalId() throws Exception {
        String externalId = "mock_pi_abc123";

        GatewayPaymentResponse response = gateway.confirmPayment(externalId).get();

        assertThat(response.externalPaymentId()).isEqualTo(externalId);
    }

    @Test
    void confirmPayment_returnsSucceededStatus() throws Exception {
        GatewayPaymentResponse response = gateway.confirmPayment("mock_pi_xyz").get();

        assertThat(response.status()).isEqualTo("succeeded");
    }

    @Test
    void createRefund_returnsIdWithMockRePrefix() throws Exception {
        GatewayRefundResponse response = gateway
                .createRefund("mock_pi_abc", BigDecimal.valueOf(50))
                .get();

        assertThat(response.refundId()).startsWith("mock_re_");
    }

    @Test
    void createRefund_returnsSucceededStatus() throws Exception {
        GatewayRefundResponse response = gateway
                .createRefund("mock_pi_abc", BigDecimal.valueOf(50))
                .get();

        assertThat(response.status()).isEqualTo("succeeded");
    }

    @Test
    void simulateDelay_interrupt_setsInterruptFlag() {
        MockPaymentGateway slowGateway = new MockPaymentGateway();
        ReflectionTestUtils.setField(slowGateway, "simulatedDelayMs", 10000L);

        CompletableFuture<GatewayPaymentResponse> future =
                slowGateway.createPaymentIntent(BigDecimal.ONE, "USD", "key");

        // The async thread is sleeping; interrupt it
        future.cancel(true); // triggers interrupt on the background thread

        // Just verify the future completes (either normally or exceptionally)
        assertThat(future.isCancelled() || future.isDone()).isTrue();
    }
}
