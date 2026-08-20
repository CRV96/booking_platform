package com.booking.platform.event_service.constants;

/**
 * Constants for MongoDB document field names and collection metadata.
 *
 * <p>Centralizes all raw field-name strings used in queries, indexes,
 * and criteria so that typos are caught at compile time and renames
 * only need to happen in one place.
 */
public final class DocumentConst {
    private DocumentConst() {}

    /** Constants for the {@code events} collection. */
    public static final class Event {
        private Event() {}

        public static final String COLLECTION_NAME = "events";

        // ── Top-level fields ─────────────────────────────────────────────
        public static final String ID = "_id";
        public static final String TITLE = "title";
        public static final String DESCRIPTION = "description";
        public static final String CATEGORY = "category";
        public static final String STATUS = "status";
        public static final String DATE_TIME = "dateTime";
        public static final String END_DATE_TIME = "endDateTime";
        public static final String TIMEZONE = "timezone";
        public static final String VENUE = "venue";
        public static final String SEAT_CATEGORIES = "seatCategories";
        public static final String IMAGES = "images";
        public static final String TAGS = "tags";

        // ── Nested organizer fields ──────────────────────────────────────
        public static final String ORGANIZER_USER_ID = "organizer.userId";

        // ── Nested venue fields ──────────────────────────────────────────
        public static final String VENUE_CITY = "venue.city";

        // ── Nested seatCategories fields ─────────────────────────────────
        public static final String SEAT_CATEGORIES_NAME = "name";
        public static final String SEAT_CATEGORIES_AVAILABLE_SEATS = "availableSeats";

        // ── Dotted paths for queries and updates ────────────────────────
        public static final String SEAT_CATEGORIES_DOT_NAME = "seatCategories.name";
        public static final String SEAT_CATEGORIES_POSITIONAL_AVAILABLE_SEATS = "seatCategories.$.availableSeats";
    }

    /**
     * Constants for the {@code event_vectors} collection — the Spring AI
     * {@code MongoDBAtlasVectorStore} that backs semantic search.
     *
     * <p>Spring AI stores each entry as {@code {_id, content, metadata, embedding}},
     * nesting all metadata under a {@code metadata} sub-document. Filterable fields
     * are therefore addressed as {@code metadata.<field>} in the vectorSearch index.
     */
    public static final class VectorStore {
        private VectorStore() {}

        public static final String COLLECTION_NAME = "event_vectors";
        public static final String INDEX_NAME = "event_vector_index";

        /**
         * Prefix for the vector document id. Without it, a 24-hex event id is coerced to a
         * Mongo {@code ObjectId} on write and fails an {@code ObjectId → String} cast on read.
         * The prefix makes the id a plain String, avoiding the coercion.
         */
        public static final String ID_PREFIX = "evt_";

        /** Field holding the embedding vector (matches spring.ai.vectorstore.mongodb.path-name). */
        public static final String PATH = "embedding";

        /** nomic-embed-text (Ollama) produces 768-dimensional vectors. */
        public static final int DIMENSIONS = 768;
        public static final String SIMILARITY = "cosine";

        // ── Metadata keys (bare) — must match spring.ai...metadata-fields-to-filter ──
        public static final String META_CATEGORY = "category";
        public static final String META_CITY = "city";
        public static final String META_STATUS = "status";
        public static final String META_DATE_TIME = "dateTime";

        // ── Filter paths for the vectorSearch index (metadata is nested) ────────────
        public static final String FILTER_CATEGORY = "metadata." + META_CATEGORY;
        public static final String FILTER_CITY = "metadata." + META_CITY;
        public static final String FILTER_STATUS = "metadata." + META_STATUS;
        public static final String FILTER_DATE_TIME = "metadata." + META_DATE_TIME;
    }
}
