package com.booking.platform.common.logging;

/**
 * Platform-wide error code registry.
 *
 * <p>Each constant maps a stable numeric code to a short description.
 * Codes are grouped by service domain using fixed numeric ranges so the
 * originating service can be identified from the code alone.
 *
 * <pre>
 * Range       Service
 * ──────────────────────────────────
 * 1000–1999   user-service
 * 2000–2999   payment-service
 * 3000–3999   event-service
 * 4000–4999   booking-service
 * 5000–5999   notification-service
 * 6000–6999   ticket-service
 * 7000–7999   analytics-service
 * 8000–8999   graphql-gateway
 * </pre>
 *
 * <p>Usage via {@link ApplicationLogger}:
 * <pre>{@code
 * ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.VERIFICATION_EMAIL_FAILED, e);
 * }</pre>
 *
 * <p>Each code is documented in {@code docs/error-codes.md}.
 */
public enum LogErrorCode {

    // ── user-service (1000–1999) ──────────────────────────────────────────────

    VERIFICATION_EMAIL_FAILED(1001, "Failed to send verification email"),
    USER_CREATION_FAILED     (1002, "Failed to create user in Keycloak"),
    USER_NOT_FOUND           (1003, "User not found"),
    USER_ALREADY_EXISTS      (1004, "User already exists"),
    USER_UPDATE_FAILED       (1005, "Failed to update user"),
    USER_DELETE_FAILED       (1006, "Failed to delete user"),
    USER_LOGIN_FAILED        (1007, "User login failed"),
    TOKEN_REFRESH_FAILED     (1008, "Token refresh failed"),
    UNVERIFIED_CLEANUP_FAILED(1009, "Failed to delete unverified user during cleanup"),

    // ── payment-service (2000–2999) ───────────────────────────────────────────

    PAYMENT_GATEWAY_UNAVAILABLE  (2001, "Payment gateway unavailable"),
    PAYMENT_PROCESSING_FAILED    (2002, "Payment processing failed"),
    PAYMENT_INTENT_FAILED        (2003, "Failed to create payment intent"),
    PAYMENT_CONFIRMATION_FAILED  (2004, "Failed to confirm payment"),
    PAYMENT_REFUND_FAILED        (2005, "Payment refund failed"),
    OUTBOX_PUBLISH_FAILED        (2006, "Failed to publish outbox event"),
    PAYMENT_RETRY_FAILED         (2007, "Payment retry attempt failed"),

    // ── event-service (3000–3999) ─────────────────────────────────────────────

    EVENT_CREATION_FAILED   (3001, "Failed to create event"),
    EVENT_UPDATE_FAILED     (3002, "Failed to update event"),
    EVENT_PUBLISH_FAILED    (3003, "Failed to publish event"),
    EVENT_CANCELLATION_FAILED(3004, "Failed to cancel event"),
    EVENT_NOT_FOUND         (3005, "Event not found"),
    SEAT_UPDATE_FAILED      (3006, "Failed to update seat availability"),
    EVENT_ACCESS_DENIED     (3007, "Access denied: insufficient permissions for event operation"),

    // ── booking-service (4000–4999) ───────────────────────────────────────────

    BOOKING_CREATION_FAILED     (4001, "Failed to create booking"),
    BOOKING_CONFIRMATION_FAILED (4002, "Failed to confirm booking"),
    BOOKING_CANCELLATION_FAILED (4003, "Failed to cancel booking"),
    BOOKING_NOT_FOUND           (4004, "Booking not found"),
    BOOKING_LOCK_FAILED         (4005, "Failed to acquire booking lock"),
    BOOKING_EVENT_PUBLISH_FAILED(4006, "Failed to publish booking event"),

    // ── notification-service (5000–5999) ──────────────────────────────────────

    EMAIL_SEND_FAILED          (5001, "Failed to send email"),
    NOTIFICATION_CONSUMER_ERROR(5002, "Error processing notification event"),

    // ── ticket-service (6000–6999) ────────────────────────────────────────────

    TICKET_GENERATION_FAILED(6001, "Failed to generate ticket"),
    TICKET_NOT_FOUND        (6002, "Ticket not found"),
    TICKET_ACCESS_DENIED    (6003, "Access denied: required role missing"),

    // ── analytics-service (7000–7999) ─────────────────────────────────────────

    ANALYTICS_EVENT_FAILED  (7001, "Failed to process analytics event"),

    // ── graphql-gateway (8000–8999) ───────────────────────────────────────────

    RATE_LIMIT_STORE_FAILED (8001, "Failed to access rate limit store"),
    GRPC_CALL_FAILED        (8002, "Downstream gRPC call failed"),
    AUTH_TOKEN_INVALID      (8003, "Authentication token is invalid or expired"),
    TLS_CONFIG_FAILED       (8004, "Failed to load TLS configuration");

    // ─────────────────────────────────────────────────────────────────────────

    private final int    code;
    private final String description;

    LogErrorCode(int code, String description) {
        this.code        = code;
        this.description = description;
    }

    public int    getCode()        { return code; }
    public String getDescription() { return description; }

    /** Returns the formatted code string used in log output, e.g. {@code [1001]}. */
    public String getFormattedCode() { return "[" + code + "]"; }
}
