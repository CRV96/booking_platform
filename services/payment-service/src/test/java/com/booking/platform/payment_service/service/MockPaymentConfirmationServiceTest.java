package com.booking.platform.payment_service.service;

import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.impl.MockPaymentConfirmationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockPaymentConfirmationServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentOutcomeService paymentOutcomeService;

    @InjectMocks private MockPaymentConfirmationService service;

    private static final String ORDER_ID = "order-1";
    private static final String EXTERNAL_ID = "mock_pi_1";

    @BeforeEach
    void setCards() {
        ReflectionTestUtils.setField(service, "successCard", "4242424242424242");
        ReflectionTestUtils.setField(service, "requiresAuthCard", "4000002500003155");
        ReflectionTestUtils.setField(service, "insufficientFundsCard", "4000000000009995");
    }

    private void paymentExists() {
        PaymentEntity payment = PaymentEntity.builder()
                .id(UUID.randomUUID()).bookingId(ORDER_ID).userId("user-1")
                .amount(new BigDecimal("50.00")).currency("USD")
                .status(PaymentStatus.PROCESSING).externalPaymentId(EXTERNAL_ID)
                .build();
        when(paymentRepository.findByBookingId(ORDER_ID)).thenReturn(Optional.of(payment));
    }

    @Test
    void successCard_marksSucceeded() {
        paymentExists();
        service.confirm(ORDER_ID, "4242 4242 4242 4242");
        verify(paymentOutcomeService).markSucceeded(EXTERNAL_ID);
        verify(paymentOutcomeService, never()).markFailed(any(), any());
    }

    @Test
    void requiresAuthCard_marksSucceeded() {
        paymentExists();
        service.confirm(ORDER_ID, "4000002500003155");
        verify(paymentOutcomeService).markSucceeded(EXTERNAL_ID);
    }

    @Test
    void declineCard_marksFailedWithCardDeclined() {
        paymentExists();
        service.confirm(ORDER_ID, "4000000000000002");
        verify(paymentOutcomeService).markFailed(EXTERNAL_ID, "card_declined");
    }

    @Test
    void unknownCard_marksFailedWithCardDeclined() {
        paymentExists();
        service.confirm(ORDER_ID, "1234123412341234");
        verify(paymentOutcomeService).markFailed(EXTERNAL_ID, "card_declined");
    }

    @Test
    void insufficientFundsCard_marksFailedWithReason() {
        paymentExists();
        service.confirm(ORDER_ID, "4000000000009995");
        verify(paymentOutcomeService).markFailed(EXTERNAL_ID, "insufficient_funds");
    }

    @Test
    void paymentNotFound_throws() {
        when(paymentRepository.findByBookingId(ORDER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm(ORDER_ID, "4242424242424242"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(paymentOutcomeService);
    }
}
