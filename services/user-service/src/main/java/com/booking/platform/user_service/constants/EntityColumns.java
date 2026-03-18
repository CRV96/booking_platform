package com.booking.platform.user_service.constants;

/**
 * Column and table name constants for read-only JPA entities mapped to Keycloak's database schema.
 * Centralizes all schema references so changes to Keycloak's table structure are easy to track.
 */
public final class EntityColumns {

    private EntityColumns() {}

    /**
     * Schema constants for {@code user_entity} table.
     */
    public static final class User {

        private User() {}

        public static final String TABLE = "user_entity";

        public static final String ID = "id";
        public static final String USERNAME = "username";
        public static final String EMAIL = "email";
        public static final String EMAIL_VERIFIED = "email_verified";
        public static final String ENABLED = "enabled";
        public static final String FIRST_NAME = "first_name";
        public static final String LAST_NAME = "last_name";
        public static final String REALM_ID = "realm_id";
        public static final String CREATED_TIMESTAMP = "created_timestamp";
    }

    /**
     * Schema constants for {@code user_attribute} table.
     */
    public static final class UserAttribute {

        private UserAttribute() {}

        public static final String TABLE = "user_attribute";

        public static final String ID = "id";
        public static final String NAME = "name";
        public static final String VALUE = "value";
        public static final String USER_ID = "user_id";
    }
}
