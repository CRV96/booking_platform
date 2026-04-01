package com.booking.platform.analytics_service.config;

import com.booking.platform.analytics_service.constants.AnalyticsConstants;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableMongoAuditing
@RequiredArgsConstructor
public class MongoConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        createEventsLogIndexes();
    }

    private void createEventsLogIndexes() {
        MongoCollection<Document> eventsLog =
                mongoTemplate.getCollection(AnalyticsConstants.Collection.EVENT_LOG);

        // Compound index for querying by event type and time range
        eventsLog.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("eventType"),
                        Indexes.descending("receivedAt")
                ),
                new IndexOptions().name("eventType_receivedAt")
        );

        // TTL index — auto-delete raw events after 90 days
        eventsLog.createIndex(
                Indexes.ascending("receivedAt"),
                new IndexOptions()
                        .name("receivedAt_ttl")
                        .expireAfter(90L, TimeUnit.DAYS)
        );
    }
}
