# Error Codes

Platform-wide structured error codes used in log output via `ApplicationLogger` and `LogErrorCode`.

Each code appears as `[NNNN]` in JSON log fields (`errorCode`) and is queryable in Grafana/Loki.
Codes are grouped by service using fixed numeric ranges, so the originating service can be
identified from the code alone.

```
Range       Service
──────────────────────────────────
1000–1999   user-service
2000–2999   payment-service
3000–3999   event-service
4000–4999   booking-service
5000–5999   notification-service
6000–6999   ticket-service
8000–8999   graphql-gateway
```

---

## user-service (1000–1999)

| Code | Constant | Description |
|------|----------|-------------|
| 1001 | `VERIFICATION_EMAIL_FAILED` | Failed to send verification email |
| 1002 | `USER_CREATION_FAILED` | Failed to create user in Keycloak |
| 1003 | `USER_NOT_FOUND` | User not found |
| 1004 | `USER_ALREADY_EXISTS` | User already exists |
| 1007 | `USER_LOGIN_FAILED` | User login failed |
| 1008 | `TOKEN_REFRESH_FAILED` | Token refresh failed |
| 1009 | `UNVERIFIED_CLEANUP_FAILED` | Failed to delete unverified user during cleanup |

## payment-service (2000–2999)

| Code | Constant | Description |
|------|----------|-------------|
| 2001 | `PAYMENT_GATEWAY_UNAVAILABLE` | Payment gateway unavailable |
| 2002 | `PAYMENT_PROCESSING_FAILED` | Payment processing failed |
| 2003 | `PAYMENT_INTENT_FAILED` | Failed to create payment intent |
| 2004 | `PAYMENT_CONFIRMATION_FAILED` | Failed to confirm payment |
| 2005 | `PAYMENT_REFUND_FAILED` | Payment refund failed |
| 2006 | `OUTBOX_PUBLISH_FAILED` | Failed to publish outbox event |
| 2007 | `PAYMENT_RETRY_FAILED` | Payment retry attempt failed |

## event-service (3000–3999)

| Code | Constant | Description |
|------|----------|-------------|
| 3003 | `EVENT_PUBLISH_FAILED` | Failed to publish event |
| 3006 | `SEAT_UPDATE_FAILED` | Failed to update seat availability |
| 3007 | `EVENT_ACCESS_DENIED` | Access denied: insufficient permissions for event operation |

## booking-service (4000–4999)

| Code | Constant | Description |
|------|----------|-------------|
| 4001 | `BOOKING_CREATION_FAILED` | Failed to create booking |
| 4003 | `BOOKING_CANCELLATION_FAILED` | Failed to cancel booking |
| 4005 | `BOOKING_LOCK_FAILED` | Failed to acquire booking lock |
| 4006 | `BOOKING_EVENT_PUBLISH_FAILED` | Failed to publish booking event |

## notification-service (5000–5999)

| Code | Constant | Description |
|------|----------|-------------|
| 5001 | `EMAIL_SEND_FAILED` | Failed to send email |
| 5002 | `NOTIFICATION_CONSUMER_ERROR` | Error processing notification event |

## ticket-service (6000–6999)

| Code | Constant | Description |
|------|----------|-------------|
| 6001 | `TICKET_GENERATION_FAILED` | Failed to generate ticket |
| 6003 | `TICKET_ACCESS_DENIED` | Access denied: required role missing |

## graphql-gateway (8000–8999)

| Code | Constant | Description |
|------|----------|-------------|
| 8001 | `RATE_LIMIT_STORE_FAILED` | Failed to access rate limit store |
| 8002 | `GRPC_CALL_FAILED` | Downstream gRPC call failed |
| 8003 | `AUTH_TOKEN_INVALID` | Authentication token is invalid or expired |
| 8004 | `TLS_CONFIG_FAILED` | Failed to load TLS configuration |
