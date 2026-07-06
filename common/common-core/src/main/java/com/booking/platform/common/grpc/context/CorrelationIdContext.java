package com.booking.platform.common.grpc.context;

import io.grpc.Context;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Holds the correlation ID for request tracing across HTTP, gRPC, and Kafka boundaries.
 *
 * <p>The correlation ID is propagated via:
 * <ul>
 *   <li>HTTP header: {@code X-Correlation-Id}</li>
 *   <li>gRPC metadata key: {@code x-correlation-id}</li>
 *   <li>Kafka record header: {@code x-correlation-id}</li>
 *   <li>SLF4J MDC key: {@code correlationId}</li>
 * </ul>
 */
public final class CorrelationIdContext {

    private CorrelationIdContext() {}

    public static final Context.Key<String> CORRELATION_ID = Context.key("correlation-id");

    public static final String MDC_KEY = "correlationId";

    public static final String HEADER_NAME = "x-correlation-id";

    public static String get() {
        return CORRELATION_ID.get();
    }

    public static String getOrGenerate() {
        String id = CORRELATION_ID.get();
        return id != null ? id : UUID.randomUUID().toString();
    }

    public static void setMdc(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    public static void clearMdc() {
        MDC.remove(MDC_KEY);
    }
}
