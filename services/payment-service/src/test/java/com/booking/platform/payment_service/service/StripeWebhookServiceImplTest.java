package com.booking.platform.payment_service.service;

import com.booking.platform.payment_service.service.impl.StripeWebhookServiceImpl;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceImplTest {

    @Mock private PaymentOutcomeService paymentOutcomeService;
    @InjectMocks private StripeWebhookServiceImpl service;

    @BeforeEach
    void setSecret() {
        ReflectionTestUtils.setField(service, "webhookSecret", "whsec_test");
    }

    private Event eventOfType(String type, PaymentIntent intent) {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        if (intent != null) {
            EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);
            when(deser.getObject()).thenReturn(Optional.of(intent));
            when(event.getDataObjectDeserializer()).thenReturn(deser);
        }
        return event;
    }

    @Test
    void paymentIntentSucceeded_marksSucceeded() throws Exception {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_1");
        Event event = eventOfType("payment_intent.succeeded", intent);

        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);
            service.process("payload", "sig");
        }

        verify(paymentOutcomeService).markSucceeded("pi_1");
    }

    @Test
    void paymentIntentFailed_marksFailedWithDefaultReason() throws Exception {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_1");
        when(intent.getLastPaymentError()).thenReturn(null);
        Event event = eventOfType("payment_intent.payment_failed", intent);

        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);
            service.process("payload", "sig");
        }

        verify(paymentOutcomeService).markFailed("pi_1", "payment_failed");
    }

    @Test
    void unknownEventType_isIgnored() throws Exception {
        Event event = eventOfType("charge.refunded", null);

        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);
            service.process("payload", "sig");
        }

        verifyNoInteractions(paymentOutcomeService);
    }

    @Test
    void invalidSignature_propagatesException() {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenThrow(new SignatureVerificationException("bad signature", "sig"));

            assertThatThrownBy(() -> service.process("payload", "sig"))
                    .isInstanceOf(SignatureVerificationException.class);
        }
        verifyNoInteractions(paymentOutcomeService);
    }
}
