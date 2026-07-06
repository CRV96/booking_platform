package com.booking.platform.analytics_service.repository;

import com.booking.platform.analytics_service.document.EventLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventLogRepository extends MongoRepository<EventLog, String> {
}
