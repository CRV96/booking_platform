package com.booking.platform.event_service.config;

import com.booking.platform.event_service.constants.DocumentConst;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.SearchIndexModel;
import com.mongodb.client.model.SearchIndexType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates the Atlas Vector Search index on the {@code event_vectors} collection that
 * backs Spring AI's {@link org.springframework.ai.vectorstore.VectorStore}.
 *
 * <p>Runs only when {@code app.semantic-search.enabled=true}. The index is created
 * programmatically (rather than via Spring AI's {@code initialize-schema}) so we
 * control the number of dimensions and the filterable metadata fields. Creation is
 * idempotent, and the index builds asynchronously (status PENDING → queryable).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.semantic-search.enabled", havingValue = "true")
public class SemanticSearchIndexConfig {

    private final MongoTemplate mongoTemplate;

    public SemanticSearchIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void createVectorSearchIndex() {
        // createSearchIndexes requires the collection to exist first.
        if (!mongoTemplate.collectionExists(DocumentConst.VectorStore.COLLECTION_NAME)) {
            mongoTemplate.createCollection(DocumentConst.VectorStore.COLLECTION_NAME);
        }

        MongoCollection<Document> collection =
                mongoTemplate.getCollection(DocumentConst.VectorStore.COLLECTION_NAME);

        if (vectorIndexExists(collection)) {
            log.info("Vector search index '{}' already exists — skipping creation",
                    DocumentConst.VectorStore.INDEX_NAME);
            return;
        }

        Document definition = new Document("fields", List.of(
                new Document("type", "vector")
                        .append("path", DocumentConst.VectorStore.PATH)
                        .append("numDimensions", DocumentConst.VectorStore.DIMENSIONS)
                        .append("similarity", DocumentConst.VectorStore.SIMILARITY),
                filterField(DocumentConst.VectorStore.FILTER_CATEGORY),
                filterField(DocumentConst.VectorStore.FILTER_CITY),
                filterField(DocumentConst.VectorStore.FILTER_STATUS),
                filterField(DocumentConst.VectorStore.FILTER_DATE_TIME)
        ));

        collection.createSearchIndexes(List.of(new SearchIndexModel(
                DocumentConst.VectorStore.INDEX_NAME, definition, SearchIndexType.vectorSearch())));

        log.info("Created Atlas vector search index '{}' on '{}' ({} dims, {} similarity) — building asynchronously",
                DocumentConst.VectorStore.INDEX_NAME, DocumentConst.VectorStore.COLLECTION_NAME,
                DocumentConst.VectorStore.DIMENSIONS, DocumentConst.VectorStore.SIMILARITY);
    }

    private boolean vectorIndexExists(MongoCollection<Document> collection) {
        return collection.listSearchIndexes().into(new ArrayList<>()).stream()
                .anyMatch(idx -> DocumentConst.VectorStore.INDEX_NAME.equals(idx.getString("name")));
    }

    private Document filterField(String path) {
        return new Document("type", "filter").append("path", path);
    }
}
