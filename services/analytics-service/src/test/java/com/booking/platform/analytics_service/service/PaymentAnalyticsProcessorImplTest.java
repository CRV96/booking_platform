package com.booking.platform.analytics_service.service;

import com.booking.platform.analytics_service.constants.AnalyticsConstants;
import com.booking.platform.analytics_service.document.EventLog;
import com.booking.platform.analytics_service.dto.PaymentDto;
import com.booking.platform.analytics_service.repository.EventLogRepository;
import com.booking.platform.analytics_service.service.impl.PaymentAnalyticsProcessorImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAnalyticsProcessorImplTest {

    @Mock private EventLogRepository eventLogRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private PaymentAnalyticsProcessorImpl processor;

    private PaymentDto completedPayment() {
        return PaymentDto.builder()
                .topic("payment.completed").key("pay-1")
                .paymentId("pay-1").bookingId("bk-1")
                .amount(150.0).currency("USD")
                .build();
    }

    private PaymentDto failedPayment() {
        return PaymentDto.builder()
                .topic("payment.failed").key("pay-2")
                .paymentId("pay-2").bookingId("bk-2")
                .reason("insufficient funds")
                .build();
    }

    private PaymentDto refundPayment() {
        return PaymentDto.builder()
                .topic("payment.refund.completed").key("pay-3")
                .paymentId("pay-3").bookingId("bk-3")
                .amount(75.0).currency("EUR").refundId("ref-1")
                .build();
    }

    // ── processPaymentCompleted ───────────────────────────────────────────────

    @Test
    void processPaymentCompleted_savesRawEvent() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        processor.processPaymentCompleted(completedPayment());

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AnalyticsConstants.Payment.COMPLETED_EVENT);
        assertThat(captor.getValue().getPayload()).containsKey(AnalyticsConstants.PAYLOAD_PAYMENT_ID);
        assertThat(captor.getValue().getPayload()).containsKey(AnalyticsConstants.PAYLOAD_BOOKING_ID);
        assertThat(captor.getValue().getPayload()).containsKey(AnalyticsConstants.PAYLOAD_AMOUNT);
    }

    @Test
    void processPaymentCompleted_upsertsDailyMetricsPaymentsCompleted() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processPaymentCompleted(completedPayment());

        verify(mongoTemplate).upsert(any(), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.DAILY_METRICS));
        assertThat(updateCaptor.getValue().toString())
                .contains(AnalyticsConstants.Payment.PAYMENTS_COMPLETED);
    }

    @Test
    void processPaymentCompleted_doesNotUpdateEventOrCategoryStats() {
        processor.processPaymentCompleted(completedPayment());

        verify(mongoTemplate, never()).upsert(any(), any(),
                eq(AnalyticsConstants.Collection.EVENT_STATS));
        verify(mongoTemplate, never()).upsert(any(), any(),
                eq(AnalyticsConstants.Collection.CATEGORY_STATS));
    }

    // ── processPaymentFailed ──────────────────────────────────────────────────

    @Test
    void processPaymentFailed_savesRawEventWithReason() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        processor.processPaymentFailed(failedPayment());

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AnalyticsConstants.Payment.FAILED_EVENT);
        assertThat(captor.getValue().getPayload()).containsEntry(
                AnalyticsConstants.PAYLOAD_REASON, "insufficient funds");
    }

    @Test
    void processPaymentFailed_upsertsDailyMetricsPaymentsFailed() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processPaymentFailed(failedPayment());

        verify(mongoTemplate).upsert(any(), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.DAILY_METRICS));
        assertThat(updateCaptor.getValue().toString())
                .contains(AnalyticsConstants.Payment.PAYMENTS_FAILED);
    }

    // ── processRefundCompleted ────────────────────────────────────────────────

    @Test
    void processRefundCompleted_savesRawEventWithRefundId() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        processor.processRefundCompleted(refundPayment());

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AnalyticsConstants.Payment.REFUND_EVENT);
        assertThat(captor.getValue().getPayload()).containsKey(AnalyticsConstants.PAYLOAD_REFUND_ID);
    }

    @Test
    void processRefundCompleted_upsertsDailyMetricsWithRefundCountAndAmount() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processRefundCompleted(refundPayment());

        verify(mongoTemplate).upsert(any(), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.DAILY_METRICS));
        String updateStr = updateCaptor.getValue().toString();
        assertThat(updateStr).contains(AnalyticsConstants.Payment.REFUNDS_COMPLETED);
        assertThat(updateStr).contains(AnalyticsConstants.Payment.TOTAL_REFUNDS);
    }

    @Test
    void processRefundCompleted_nullReasonNotInPayload() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        processor.processRefundCompleted(refundPayment()); // reason = null

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).doesNotContainKey(AnalyticsConstants.PAYLOAD_REASON);
    }
}
