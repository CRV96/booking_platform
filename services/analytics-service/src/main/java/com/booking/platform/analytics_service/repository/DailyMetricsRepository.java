package com.booking.platform.analytics_service.repository;

import com.booking.platform.analytics_service.document.DailyMetrics;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DailyMetricsRepository extends MongoRepository<DailyMetrics, String> {

    Optional<DailyMetrics> findByDate(String date);
}
