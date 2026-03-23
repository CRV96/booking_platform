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

        // ── Nested venue fields ──────────────────────────────────────────
        public static final String VENUE_CITY = "venue.city";

        // ── Nested seatCategories fields ─────────────────────────────────
        public static final String SEAT_CATEGORIES_NAME = "name";
        public static final String SEAT_CATEGORIES_AVAILABLE_SEATS = "availableSeats";

        // ── Dotted paths for queries and updates ────────────────────────
        public static final String SEAT_CATEGORIES_DOT_NAME = "seatCategories.name";
        public static final String SEAT_CATEGORIES_POSITIONAL_AVAILABLE_SEATS = "seatCategories.$.availableSeats";
    }
}
