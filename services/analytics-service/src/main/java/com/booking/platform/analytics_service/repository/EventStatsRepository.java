package com.booking.platform.analytics_service.repository;

import com.booking.platform.analytics_service.document.EventStats;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EventStatsRepository extends MongoRepository<EventStats, String> {

    Optional<EventStats> findByEventId(String eventId);
}
