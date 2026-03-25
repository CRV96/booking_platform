package com.booking.platform.notification_service.constants;

public final class NotificationConst {
    private NotificationConst(){
        // Private constructor to prevent instantiation
    }

    public static final String DLT_GROUP = "notification-service-dlt-group";
    public static final String DLT_LISTENER_FACTORY = "dltListenerFactory";

    /** Health check detail keys and values used by {@code KafkaHealthIndicator}. */
    public static final class HealthDetails {
        private HealthDetails() {}

        public static final String BROKER_STATUS = "brokerStatus";
        public static final String TOPIC_COUNT = "topicCount";
        public static final String REQUIRED_TOPICS_PRESENT = "requiredTopicsPresent";
        public static final String MISSING_TOPICS = "missingTopics";
        public static final String ERROR = "error";

        public static final String REACHABLE = "reachable";
        public static final String UNREACHABLE = "unreachable";
    }

    /** Dev stub email patterns — replaced by user-service gRPC lookup in P3+. */
    public static final class DevStubEmails {
        private DevStubEmails() {}

        public static final String USER_FORMAT = "user-%s@booking-platform.dev";
        public static final String ATTENDEES_FORMAT = "attendees-%s@booking-platform.dev";
    }
}
