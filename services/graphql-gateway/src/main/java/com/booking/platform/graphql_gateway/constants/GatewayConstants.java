package com.booking.platform.graphql_gateway.constants;

public final class GatewayConstants {

    private GatewayConstants() {}

    public static final class GraphQL {
        private GraphQL() {}

        public static final String PATH                = "/graphql";
        public static final String EXTENSION_CODE      = "code";
        public static final String EXTENSION_TIMESTAMP = "timestamp";
        public static final String EXTENSION_PATH      = "path";
    }

    public static final class Http {
        private Http() {}

        public static final String HEADER_FORWARDED_FOR        = "X-Forwarded-For";
        public static final String HEADER_RETRY_AFTER          = "Retry-After";
        public static final String HEADER_RATE_LIMIT           = "X-RateLimit-Limit";
        public static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
        public static final String HEADER_RATE_LIMIT_RESET     = "X-RateLimit-Reset";
    }

    public static final class RateLimit {
        private RateLimit() {}

        public static final String KEY_PREFIX     = "ratelimit:";
        public static final String IDENTITY_USER  = "user:";
        public static final String IDENTITY_IP    = "ip:";
    }

    public static final class Security {
        private Security() {}

        public static final String ROLE_PREFIX                  = "ROLE_";
        public static final String BEARER_PREFIX                = "Bearer ";
        public static final String GRPC_AUTHORIZATION_HEADER    = "authorization";
        public static final String KEYCLOAK_REALM_ACCESS_CLAIM  = "realm_access";
        public static final String KEYCLOAK_ROLES_CLAIM         = "roles";
    }
}
