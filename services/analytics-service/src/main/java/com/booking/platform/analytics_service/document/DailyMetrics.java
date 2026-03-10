package com.booking.platform.analytics_service.document;

import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Daily platform-wide metrics — one document per calendar day.
 *
 * <p>Counters are incremented via {@code $inc} upserts as events arrive.
 * The {@code date} field uses ISO date format (e.g. "2026-03-04").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(BkgAnalyticsConstants.BkgDocumentConstants.DAILY_METRICS_COLLECTION)
public class DailyMetrics {

    @Id
    private String id;

    @Indexed(unique = true)
    private String date;

    // Event lifecycle counters
    private int eventsCreated;
    private int eventsPublished;
    private int eventsCancelled;

    // Booking lifecycle counters
    private int bookingsCreated;
    private int bookingsConfirmed;
    private int bookingsCancelled;

    // Payment lifecycle counters
    private int paymentsCompleted;
    private int paymentsFailed;
    private int refundsCompleted;

    // Revenue
    private double totalRevenue;
    private double totalRefunds;

    private Instant lastUpdated;
}
