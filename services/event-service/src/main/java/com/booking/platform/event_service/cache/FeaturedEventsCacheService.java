package com.booking.platform.event_service.cache;

import com.booking.platform.event_service.config.CacheConfig;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled service that pre-populates the {@code events:featured} Redis cache
 * with the top 20 upcoming published events.
 *
 * <p>Runs every 2 minutes to keep the featured list fresh.
 * The cache TTL is also 2 minutes, so the scheduled job acts as a refresh mechanism
 * preventing the cache from ever being cold for featured event listings.
 *
 * <p>The featured events cache key is {@code "featured"} — a fixed key since
 * there is only one featured list (no per-user or per-filter variants here).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FeaturedEventsCacheService {

    public static final String FEATURED_CACHE_KEY = "featured";

    private final MongoTemplate mongoTemplate;

    /**
     * Refreshes the featured events cache every 2 minutes.
     * Fetches the next 20 upcoming published events sorted by dateTime ascending.
     */
    @Scheduled(fixedRateString = "${cache.featured-events.refresh-rate-ms:120000}")
    @CachePut(value = CacheConfig.CACHE_EVENTS_FEATURED, key = "'" + FEATURED_CACHE_KEY + "'")
    public List<EventDocument> refreshFeaturedEvents() {
        log.debug("Refreshing featured events cache");

        Query query = new Query(
                Criteria.where("status").is(EventStatus.PUBLISHED)
                        .and("dateTime").gte(Instant.now())
        )
                .with(Sort.by(Sort.Direction.ASC, "dateTime"))
                .limit(20);

        List<EventDocument> featured = mongoTemplate.find(query, EventDocument.class);

        log.info("Featured events cache refreshed: {} events", featured.size());
        return featured;
    }
}
