package com.booking.platform.analytics_service.document;

import com.booking.platform.analytics_service.constants.AnalyticsConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Per-category aggregate statistics — one document per event category
 * (e.g. CONCERT, CONFERENCE, SPORTS).
 *
 * <p>Updated via {@code $inc} upserts whenever event or booking events
 * reference this category.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(AnalyticsConstants.Collection.CATEGORY_STATS)
public class CategoryStats {

    @Id
    private String id;

    @Indexed(unique = true)
    private String category;

    private int totalEvents;
    private int publishedEvents;
    private int totalBookings;
    private double totalRevenue;

    private Instant lastUpdated;
}
