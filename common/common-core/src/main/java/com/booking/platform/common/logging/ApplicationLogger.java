package com.booking.platform.common.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.event.Level;

/**
 * Centralised logging utility for the Booking Platform.
 *
 * <p>Provides two overloads of {@code logMessage}:
 *
 * <ul>
 *   <li><b>With error code</b> — for {@code ERROR} and {@code WARN} log entries that
 *       represent a known failure condition. The error code is prepended to the message
 *       and also written to the MDC under the key {@code errorCode}, making it available
 *       as a structured field in JSON log output (Loki / Grafana queries).</li>
 *   <li><b>Without error code</b> — for {@code DEBUG}, {@code INFO}, and any other
 *       diagnostic log entry that does not represent a failure. Delegates directly to
 *       SLF4J with full varargs support.</li>
 * </ul>
 *
 * <p>Usage examples:
 * <pre>{@code
 * // ERROR with a code and exception
 * ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.VERIFICATION_EMAIL_FAILED, e);
 *
 * // WARN with a code and a contextual message
 * ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.USER_NOT_FOUND, "userId='{}'", userId);
 *
 * // INFO — no code
 * ApplicationLogger.logMessage(log, Level.INFO, "User registered successfully: {}", userId);
 *
 * // DEBUG — no code
 * ApplicationLogger.logMessage(log, Level.DEBUG, "Fetching user by ID: {}", userId);
 * }</pre>
 *
 * <p>Log format with error code:
 * <pre>
 * ERROR [1001] Failed to send verification email — userId='abc123'
 * </pre>
 *
 * <p>JSON output (via Logstash encoder) will include:
 * <pre>
 * { "level": "ERROR", "errorCode": "1001", "message": "[1001] Failed to send verification email ..." }
 * </pre>
 */
public final class ApplicationLogger {

    private static final String MDC_ERROR_CODE_KEY = "errorCode";

    private ApplicationLogger() {
        // Utility class — not instantiable
    }

    // ── With error code ───────────────────────────────────────────────────────

    /**
     * Logs a message at the given level with an error code and a causing exception.
     *
     * @param logger the SLF4J logger of the calling class
     * @param level  the log level (typically {@code ERROR} or {@code WARN})
     * @param code   the error code identifying this failure condition
     * @param cause  the exception that triggered this log entry (may be {@code null})
     */
    public static void logMessage(Logger logger, Level level, LogErrorCode code, Throwable cause) {
        String message = code.getFormattedCode() + " " + code.getDescription();
        MDC.put(MDC_ERROR_CODE_KEY, String.valueOf(code.getCode()));
        try {
            logger.atLevel(level).setCause(cause).log(message);
        } finally {
            MDC.remove(MDC_ERROR_CODE_KEY);
        }
    }

    /**
     * Logs a message at the given level with an error code, a contextual message
     * template, and an optional causing exception.
     *
     * <p>The contextual message supports SLF4J-style placeholders ({@code {}}).
     * The last element of {@code args} is treated as the cause if it is a
     * {@link Throwable}.
     *
     * @param logger  the SLF4J logger of the calling class
     * @param level   the log level (typically {@code ERROR} or {@code WARN})
     * @param code    the error code identifying this failure condition
     * @param message a SLF4J message template, e.g. {@code "userId='{}'"}
     * @param args    message arguments; last arg may be a {@link Throwable}
     */
    public static void logMessage(Logger logger, Level level, LogErrorCode code,
                                  String message, Object... args) {
        String prefixed = code.getFormattedCode() + " " + code.getDescription() + " — " + message;
        MDC.put(MDC_ERROR_CODE_KEY, String.valueOf(code.getCode()));
        try {
            Throwable cause = extractCause(args);
            if (cause != null) {
                logger.atLevel(level).setCause(cause).log(prefixed, args);
            } else {
                logger.atLevel(level).log(prefixed, args);
            }
        } finally {
            MDC.remove(MDC_ERROR_CODE_KEY);
        }
    }

    // ── Without error code ────────────────────────────────────────────────────

    /**
     * Logs a plain message at the given level with no error code.
     * Intended for {@code DEBUG}, {@code INFO}, and informational {@code WARN} entries.
     *
     * @param logger  the SLF4J logger of the calling class
     * @param level   the log level
     * @param message a SLF4J message template
     * @param args    message arguments
     */
    public static void logMessage(Logger logger, Level level, String message, Object... args) {
        logger.atLevel(level).log(message, args);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Throwable extractCause(Object[] args) {
        if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable t) {
            return t;
        }
        return null;
    }
}
