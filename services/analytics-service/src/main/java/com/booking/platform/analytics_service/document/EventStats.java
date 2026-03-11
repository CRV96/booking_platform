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
 * Per-event aggregate statistics — one document per event (concert, conference, etc.).
 *
 * <p>Updated via {@code $inc} / {@code $set} upserts whenever booking or refund
 * events reference this event's ID.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(BkgAnalyticsConstants.Collection.EVENT_STATS)
public class EventStats {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    private String eventTitle;
    private String category;

    private int totalBookings;
    private int confirmedBookings;
    private int cancelledBookings;

    private double totalRevenue;
    private double totalRefunds;
    private String currency;

    private Instant lastUpdated;
}
