package com.booking.platform.payment_service.gateway.impl;

import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import com.booking.platform.payment_service.exception.PaymentGatewayException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Stripe SDK implementation of {@link PaymentGateway}.
 *
 * <p>Activated when {@code payment.gateway.type=stripe} in configuration.
 * Uses the Stripe API key from {@code stripe.api-key} property.
 *
 * <p>Amounts are converted from standard currency units (dollars) to the smallest
 * unit (cents) before calling Stripe, because Stripe expects amounts in cents.
 *
 * <p>In test mode, {@code confirmPayment()} uses the {@code pm_card_visa} test
 * payment method to auto-succeed without real card details.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.gateway.type", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

    @Value("${stripe.api-key}")
    private String apiKey;

    @PostConstruct
    void init() {
        Stripe.apiKey = apiKey;
        log.info("Stripe gateway initialized (key ending in ...{})",
                apiKey.length() > 8 ? apiKey.substring(apiKey.length() - 4) : "****");
    }

    @Override
    public GatewayPaymentResponse createPaymentIntent(BigDecimal amount, String currency, String idempotencyKey) {
        try {
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency.toLowerCase())
                    .putMetadata("idempotency_key", idempotencyKey)
                    .addPaymentMethodType("card")
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            log.info("Stripe PaymentIntent created: id='{}', status='{}', amount={} {}",
                    intent.getId(), intent.getStatus(), amount, currency);

            return new GatewayPaymentResponse(intent.getId(), intent.getStatus(), "card");

        } catch (StripeException e) {
            log.error("Stripe createPaymentIntent failed: code='{}', message='{}'",
                    e.getCode(), e.getMessage());
            throw new PaymentGatewayException("Failed to create payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayPaymentResponse confirmPayment(String externalPaymentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(externalPaymentId);

            PaymentIntentConfirmParams params = PaymentIntentConfirmParams.builder()
                    .setPaymentMethod("pm_card_visa") // Stripe test payment method
                    .build();

            intent = intent.confirm(params);

            log.info("Stripe PaymentIntent confirmed: id='{}', status='{}'",
                    intent.getId(), intent.getStatus());

            String paymentMethod = intent.getPaymentMethod() != null
                    ? intent.getPaymentMethod()
                    : "card";

            return new GatewayPaymentResponse(intent.getId(), intent.getStatus(), paymentMethod);

        } catch (StripeException e) {
            log.error("Stripe confirmPayment failed: code='{}', message='{}'",
                    e.getCode(), e.getMessage());
            throw new PaymentGatewayException("Failed to confirm payment: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayRefundResponse createRefund(String externalPaymentId, BigDecimal amount) {
        try {
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(externalPaymentId)
                    .setAmount(amountInCents)
                    .build();

            Refund refund = Refund.create(params);

            log.info("Stripe Refund created: id='{}', status='{}', amount={}",
                    refund.getId(), refund.getStatus(), amount);

            return new GatewayRefundResponse(refund.getId(), refund.getStatus());

        } catch (StripeException e) {
            log.error("Stripe createRefund failed: code='{}', message='{}'",
                    e.getCode(), e.getMessage());
            throw new PaymentGatewayException("Failed to create refund: " + e.getMessage(), e);
        }
    }
}
