package com.booking.platform.payment_service.gateway.impl;

import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.exception.PaymentGatewayUnavailableException;
import com.stripe.Stripe;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StripePaymentGatewayTest {

    @Test
    void init_setsStripeApiKey() {
        StripePaymentGateway gateway = new StripePaymentGateway();
        ReflectionTestUtils.setField(gateway, "apiKey", "sk_test_my_key");

        gateway.init();

        assertThat(Stripe.apiKey).isEqualTo("sk_test_my_key");
    }

    @Test
    void createPaymentIntentFallback_returnsFailedFuture() throws Exception {
        StripePaymentGateway gateway = new StripePaymentGateway();
        ReflectionTestUtils.setField(gateway, "apiKey", "sk_test_key");

        Method fallback = StripePaymentGateway.class.getDeclaredMethod(
                "createPaymentIntentFallback", BigDecimal.class, String.class, String.class, Throwable.class);
        fallback.setAccessible(true);

        RuntimeException cause = new RuntimeException("circuit open");
        @SuppressWarnings("unchecked")
        CompletableFuture<GatewayPaymentResponse> future = (CompletableFuture<GatewayPaymentResponse>)
                fallback.invoke(gateway, BigDecimal.valueOf(100), "USD", "key-1", cause);

        assertThat(future.isCompletedExceptionally()).isTrue();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertThat(ex.getCause()).isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(ex.getCause().getMessage()).contains("circuit open");
    }

    @Test
    void confirmPaymentFallback_returnsFailedFuture() throws Exception {
        StripePaymentGateway gateway = new StripePaymentGateway();
        ReflectionTestUtils.setField(gateway, "apiKey", "sk_test_key");

        Method fallback = StripePaymentGateway.class.getDeclaredMethod(
                "confirmPaymentFallback", String.class, Throwable.class);
        fallback.setAccessible(true);

        RuntimeException cause = new RuntimeException("broker timeout");
        @SuppressWarnings("unchecked")
        CompletableFuture<GatewayPaymentResponse> future = (CompletableFuture<GatewayPaymentResponse>)
                fallback.invoke(gateway, "pi_test_123", cause);

        assertThat(future.isCompletedExceptionally()).isTrue();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertThat(ex.getCause()).isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(ex.getCause().getMessage()).contains("broker timeout");
    }

    @Test
    void createRefundFallback_returnsFailedFuture() throws Exception {
        StripePaymentGateway gateway = new StripePaymentGateway();
        ReflectionTestUtils.setField(gateway, "apiKey", "sk_test_key");

        Method fallback = StripePaymentGateway.class.getDeclaredMethod(
                "createRefundFallback", String.class, BigDecimal.class, Throwable.class);
        fallback.setAccessible(true);

        RuntimeException cause = new RuntimeException("bulkhead full");
        @SuppressWarnings("unchecked")
        CompletableFuture<GatewayRefundResponse> future = (CompletableFuture<GatewayRefundResponse>)
                fallback.invoke(gateway, "pi_test_456", BigDecimal.valueOf(50), cause);

        assertThat(future.isCompletedExceptionally()).isTrue();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertThat(ex.getCause()).isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(ex.getCause().getMessage()).contains("bulkhead full");
    }

    @Test
    void createPaymentIntentFallback_messageContainsCause() throws Exception {
        StripePaymentGateway gateway = new StripePaymentGateway();
        ReflectionTestUtils.setField(gateway, "apiKey", "sk_test_key");

        Method fallback = StripePaymentGateway.class.getDeclaredMethod(
                "createPaymentIntentFallback", BigDecimal.class, String.class, String.class, Throwable.class);
        fallback.setAccessible(true);

        RuntimeException cause = new RuntimeException("stripe service is down");
        @SuppressWarnings("unchecked")
        CompletableFuture<GatewayPaymentResponse> future = (CompletableFuture<GatewayPaymentResponse>)
                fallback.invoke(gateway, BigDecimal.valueOf(200), "EUR", "key-2", cause);

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertThat(ex.getCause().getMessage()).contains("stripe service is down");
    }
}
