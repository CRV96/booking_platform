package com.booking.platform.analytics_service.repository;

import com.booking.platform.analytics_service.document.CategoryStats;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CategoryStatsRepository extends MongoRepository<CategoryStats, String> {

    Optional<CategoryStats> findByCategory(String category);
}
