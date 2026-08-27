package com.booking.platform.payment_service.controller;

import com.booking.platform.payment_service.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock private StripeWebhookService stripeWebhookService;
    @InjectMocks private StripeWebhookController controller;

    private static final String PAYLOAD = "{\"id\":\"evt_1\"}";
    private static final String SIG = "t=1,v1=abc";

    @Test
    void handle_success_returns200() throws Exception {
        ResponseEntity<Void> response = controller.handle(PAYLOAD, SIG);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(stripeWebhookService).process(PAYLOAD, SIG);
    }

    @Test
    void handle_invalidSignature_returns400() throws Exception {
        doThrow(new SignatureVerificationException("bad signature", SIG))
                .when(stripeWebhookService).process(PAYLOAD, SIG);

        ResponseEntity<Void> response = controller.handle(PAYLOAD, SIG);

        // 400 — a bad signature won't fix on retry, so Stripe should not redeliver
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handle_processingError_returns500() throws Exception {
        doThrow(new RuntimeException("db down"))
                .when(stripeWebhookService).process(PAYLOAD, SIG);

        ResponseEntity<Void> response = controller.handle(PAYLOAD, SIG);

        // 500 — transient; Stripe should redeliver later
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
