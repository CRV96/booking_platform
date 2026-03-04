package com.booking.platform.analytics_service.service.impl;

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
    public void processPaymentCompleted(String topic, String key,
                                        String paymentId, String bookingId,
                                        double amount, String currency) {
        saveRawEvent("PaymentCompletedEvent", topic, key, Map.of(
                "paymentId", paymentId, "bookingId", bookingId,
                "amount", amount, "currency", currency));

        // Payment events only update daily_metrics (no eventId available)
        upsertDailyMetrics(new Update().inc("paymentsCompleted", 1));

        log.debug("Processed PaymentCompletedEvent: paymentId='{}', bookingId='{}'", paymentId, bookingId);
    }

    @Override
    public void processPaymentFailed(String topic, String key,
                                     String paymentId, String bookingId, String reason) {
        saveRawEvent("PaymentFailedEvent", topic, key, Map.of(
                "paymentId", paymentId, "bookingId", bookingId, "reason", reason));

        // Payment events only update daily_metrics (no eventId available)
        upsertDailyMetrics(new Update().inc("paymentsFailed", 1));

        log.debug("Processed PaymentFailedEvent: paymentId='{}', bookingId='{}'", paymentId, bookingId);
    }

    @Override
    public void processRefundCompleted(String topic, String key,
                                       String paymentId, String bookingId,
                                       String refundId, double amount, String currency) {
        saveRawEvent("RefundCompletedEvent", topic, key, Map.of(
                "paymentId", paymentId, "bookingId", bookingId,
                "refundId", refundId, "amount", amount, "currency", currency));

        // Payment events only update daily_metrics (no eventId available)
        upsertDailyMetrics(new Update()
                .inc("refundsCompleted", 1)
                .inc("totalRefunds", amount));

        log.debug("Processed RefundCompletedEvent: paymentId='{}', bookingId='{}', amount={}",
                paymentId, bookingId, amount);
    }
}
