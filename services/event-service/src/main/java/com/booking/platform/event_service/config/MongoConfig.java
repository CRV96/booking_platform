package com.booking.platform.event_service.config;

import com.booking.platform.event_service.constants.DocumentConst;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    private final MongoTemplate mongoTemplate;

    public MongoConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void createIndexes() {
        MongoCollection<Document> events = mongoTemplate.getCollection(DocumentConst.Event.COLLECTION_NAME);

        // Index on nested venue.city field — cannot use @Indexed on embedded document fields
        events.createIndex(
                Indexes.ascending(DocumentConst.Event.VENUE_CITY),
                new IndexOptions().name("venue_city")
        );

        // Compound index for category/status/dateTime filtering
        events.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending(DocumentConst.Event.CATEGORY),
                        Indexes.ascending(DocumentConst.Event.STATUS),
                        Indexes.ascending(DocumentConst.Event.DATE_TIME)
                ),
                new IndexOptions().name("category_status_dateTime")
        );

        // Text index on title (weight 3) and description for full-text search
        // @TextIndexed annotations alone are not enough without auto-index-creation=true
        events.createIndex(
                new Document(DocumentConst.Event.TITLE, "text")
                        .append(DocumentConst.Event.DESCRIPTION, "text"),
                new IndexOptions()
                        .name("text_search")
                        .weights(new Document(DocumentConst.Event.TITLE, 3)
                                .append(DocumentConst.Event.DESCRIPTION, 1))
        );
    }
}
