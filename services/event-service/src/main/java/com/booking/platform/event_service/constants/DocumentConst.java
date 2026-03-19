package com.booking.platform.event_service.constants;

/**
 * Constants related to MongoDB document structure for event storage.
 */
public final class DocumentConst {
    private DocumentConst() {
    }

    /**
     * Constants for the Event document structure and collection name.
     */
    public final class Event {
        private Event() {
        }
        /**
         * MongoDB collection name for event documents.
         */
        public static final String COLLECTION_NAME = "events";
    }


}
