package com.booking.platform.analytics_service.service.impl;

import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants.BkgPaymentConstants;
import com.booking.platform.analytics_service.dto.PaymentDto;
import com.booking.platform.analytics_service.repository.EventLogRepository;
import com.booking.platform.analytics_service.service.PaymentAnalyticsProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Processes payment-domain Kafka events.
 *
 * <p>Updates:
 * <ul>
 *   <li>{@code events_log} — raw event append (all events)</li>
 *   <li>{@code daily_metrics} — payment lifecycle counters + refund amounts</li>
 * </ul>
 *
 * <p>Payment events only update {@code daily_metrics} because they carry
 * {@code bookingId} but not {@code eventId}. Per-event revenue tracking
 * is handled by {@link BookingAnalyticsProcessorImpl} via booking events.
 */
@Slf4j
@Service
public class PaymentAnalyticsProcessorImpl extends BaseAnalyticsProcessor
        implements PaymentAnalyticsProcessor {

    public PaymentAnalyticsProcessorImpl(EventLogRepository eventLogRepository,
                                         MongoTemplate mongoTemplate) {
        super(eventLogRepository, mongoTemplate);
    }

    @Override
    public void processPaymentCompleted(PaymentDto payment) {
        saveRawEvent(BkgPaymentConstants.PAYMENT_COMPLETED_EVENT, payment, getPayload(payment));

        // Payment events only update daily_metrics (no eventId available)
        upsertDailyMetrics(new Update()
                .inc(BkgPaymentConstants.PAYMENT_COMPLETED_TOPIC, 1));

        log.debug("Processed PaymentCompletedEvent: paymentId='{}', bookingId='{}'",
                payment.paymentId(), payment.bookingId());
    }

    @Override
    public void processPaymentFailed(PaymentDto payment) {
        saveRawEvent(BkgPaymentConstants.PAYMENT_FAILED_EVENT, payment, getPayload(payment));

        // Payment events only update daily_metrics (no eventId available)
        upsertDailyMetrics(new Update()
                .inc(BkgPaymentConstants.PAYMENT_FAILED_TOPIC, 1));

        log.debug("Processed PaymentFailedEvent: paymentId='{}', bookingId='{}'",
                payment.paymentId(), payment.bookingId());
    }

    @Override
    public void processRefundCompleted(PaymentDto payment) {
        saveRawEvent(BkgPaymentConstants.PAYMENT_REFUND_EVENT, payment, getPayload(payment));

        // Payment events only update daily_metrics (no eventId available)
        upsertDailyMetrics(new Update()
                .inc(BkgPaymentConstants.PAYMENT_REFUND_COMPLETED, 1)
                .inc(BkgPaymentConstants.PAYMENT_REFUND_TOTAL_REFUNDS, payment.amount()));

        log.debug("Processed RefundCompletedEvent: paymentId='{}', bookingId='{}', amount={}",
                payment.paymentId(), payment.bookingId(), payment.amount());
    }
}
