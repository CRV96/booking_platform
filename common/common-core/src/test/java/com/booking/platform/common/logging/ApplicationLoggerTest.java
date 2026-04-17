package com.booking.platform.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses a real Logger backed by Logback's ListAppender so that SLF4J
 * overload resolution (log(String, Object) vs log(String, Object...))
 * is handled by the actual SLF4J/Logback implementation, not mocks.
 */
class ApplicationLoggerTest {

    private static final String LOGGER_NAME = "test.ApplicationLoggerTest";

    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LOGGER_NAME);
        logger.setLevel(ch.qos.logback.classic.Level.ALL);

        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        MDC.remove("errorCode");
    }

    // ── logMessage(Logger, Level, LogErrorCode, Throwable) ────────────────────

    @Test
    void logMessage_withCodeAndCause_formatsMessageWithCode() {
        ApplicationLogger.logMessage(logger, Level.ERROR, LogErrorCode.VERIFICATION_EMAIL_FAILED,
                new RuntimeException("smtp down"));

        ILoggingEvent event = singleEvent();
        assertThat(event.getFormattedMessage())
                .isEqualTo("[1001] Failed to send verification email");
    }

    @Test
    void logMessage_withCodeAndCause_logsAtCorrectLevel() {
        ApplicationLogger.logMessage(logger, Level.ERROR, LogErrorCode.VERIFICATION_EMAIL_FAILED,
                new RuntimeException("boom"));

        assertThat(singleEvent().getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
    }

    @Test
    void logMessage_withCodeAndCause_attachesThrowableToEvent() {
        RuntimeException cause = new RuntimeException("root cause");

        ApplicationLogger.logMessage(logger, Level.ERROR, LogErrorCode.VERIFICATION_EMAIL_FAILED, cause);

        assertThat(singleEvent().getThrowableProxy()).isNotNull();
        assertThat(singleEvent().getThrowableProxy().getMessage()).isEqualTo("root cause");
    }

    @Test
    void logMessage_withCodeAndNullCause_logsWithoutThrowable() {
        ApplicationLogger.logMessage(logger, Level.WARN, LogErrorCode.USER_NOT_FOUND, (Throwable) null);

        assertThat(singleEvent().getThrowableProxy()).isNull();
    }

    @Test
    void logMessage_withCodeAndCause_setsMdcDuringCall() {
        ApplicationLogger.logMessage(logger, Level.ERROR, LogErrorCode.VERIFICATION_EMAIL_FAILED,
                new RuntimeException());

        // MDC is captured by the event at log time (inside the try block when MDC is set)
        assertThat(singleEvent().getMDCPropertyMap().get("errorCode")).isEqualTo("1001");
    }

    @Test
    void logMessage_withCodeAndCause_clearsMdcAfterCall() {
        ApplicationLogger.logMessage(logger, Level.ERROR, LogErrorCode.VERIFICATION_EMAIL_FAILED,
                new RuntimeException());

        assertThat(MDC.get("errorCode")).isNull();
    }

    // ── logMessage(Logger, Level, LogErrorCode, String, Object...) ────────────

    @Test
    void logMessage_withCodeAndMessage_prefixesMessageWithCodeAndDescription() {
        ApplicationLogger.logMessage(logger, Level.WARN, LogErrorCode.USER_NOT_FOUND,
                "userId='{}'", "abc123");

        String msg = singleEvent().getFormattedMessage();
        assertThat(msg)
                .startsWith("[1003]")
                .contains("User not found")
                .contains("userId='abc123'"); // SLF4J fills in the placeholder
    }

    @Test
    void logMessage_withCodeAndMessage_usesDashSeparatorBeforeContextMessage() {
        ApplicationLogger.logMessage(logger, Level.WARN, LogErrorCode.USER_NOT_FOUND,
                "userId='{}'", "x");

        assertThat(singleEvent().getFormattedMessage()).contains(" — ");
    }

    @Test
    void logMessage_withCodeAndMessage_setsMdcWithNumericCode() {
        ApplicationLogger.logMessage(logger, Level.WARN, LogErrorCode.USER_NOT_FOUND,
                "userId='{}'", "abc123");

        assertThat(singleEvent().getMDCPropertyMap().get("errorCode")).isEqualTo("1003");
    }

    @Test
    void logMessage_withCodeAndMessage_clearsMdcAfterCall() {
        ApplicationLogger.logMessage(logger, Level.WARN, LogErrorCode.USER_NOT_FOUND,
                "userId='{}'", "abc123");

        assertThat(MDC.get("errorCode")).isNull();
    }

    @Test
    void logMessage_withCodeMessageAndThrowableLastArg_extractsCause() {
        RuntimeException cause = new RuntimeException("db error");

        ApplicationLogger.logMessage(logger, Level.ERROR, LogErrorCode.USER_NOT_FOUND,
                "userId='{}'", "abc123", cause);

        assertThat(singleEvent().getThrowableProxy()).isNotNull();
        assertThat(singleEvent().getThrowableProxy().getMessage()).isEqualTo("db error");
    }

    @Test
    void logMessage_withCodeAndMessageNoThrowable_hasNoThrowable() {
        ApplicationLogger.logMessage(logger, Level.WARN, LogErrorCode.USER_NOT_FOUND,
                "userId='{}'", "abc123");

        assertThat(singleEvent().getThrowableProxy()).isNull();
    }

    // ── logMessage(Logger, Level, String, Object...) ──────────────────────────

    @Test
    void logMessage_withoutCode_logsMessageDirectly() {
        ApplicationLogger.logMessage(logger, Level.INFO, "User registered: {}", "abc123");

        assertThat(singleEvent().getFormattedMessage()).isEqualTo("User registered: abc123");
    }

    @Test
    void logMessage_withoutCode_doesNotSetMdc() {
        ApplicationLogger.logMessage(logger, Level.INFO, "some message");

        assertThat(singleEvent().getMDCPropertyMap()).doesNotContainKey("errorCode");
    }

    @Test
    void logMessage_withoutCode_logsAtCorrectLevel() {
        ApplicationLogger.logMessage(logger, Level.DEBUG, "debug info");

        assertThat(singleEvent().getLevel()).isEqualTo(ch.qos.logback.classic.Level.DEBUG);
    }

    @Test
    void logMessage_withoutCode_doesNotClobberExistingMdc() {
        MDC.put("errorCode", "existing");

        ApplicationLogger.logMessage(logger, Level.INFO, "some message");

        assertThat(MDC.get("errorCode")).isEqualTo("existing");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ILoggingEvent singleEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }
}
