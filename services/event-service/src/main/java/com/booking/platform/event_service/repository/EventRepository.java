package com.booking.platform.event_service.repository;

import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.enums.EventStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends MongoRepository<EventDocument, String> {

    List<EventDocument> findByStatusAndCategory(EventStatus status, EventCategory category);

    List<EventDocument> findByVenueCity(String city);
}
