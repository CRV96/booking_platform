package com.booking.platform.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

/**
 * Custom Logback converter that maps each log level to a distinct ANSI colour code.
 *
 * <p>Registered in {@code logback-spring.xml} as:
 * <pre>{@code
 * <conversionRule conversionWord="lvl"
 *     class="com.booking.platform.common.logging.LevelColourConverter"/>
 * }</pre>
 *
 * <p>Usage in pattern: {@code %lvl(%-5p)}
 *
 * <table>
 *   <tr><th>Level</th><th>Colour</th></tr>
 *   <tr><td>ERROR</td><td>Bold red</td></tr>
 *   <tr><td>WARN</td><td>Yellow</td></tr>
 *   <tr><td>INFO</td><td>Green</td></tr>
 *   <tr><td>DEBUG</td><td>White</td></tr>
 *   <tr><td>TRACE</td><td>Blue</td></tr>
 * </table>
 */
public class LevelColourConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {

    // ANSI escape codes
    private static final String BOLD_RED = "1;31";
    private static final String YELLOW   = "33";
    private static final String GREEN    = "32";
    private static final String WHITE    = "97";  // bright white — visible on dark and light themes
    private static final String BLUE     = "34";

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        int level = event.getLevel().toInt();
        if (level >= Level.ERROR_INTEGER) return BOLD_RED;
        if (level >= Level.WARN_INTEGER)  return YELLOW;
        if (level >= Level.INFO_INTEGER)  return GREEN;
        if (level >= Level.DEBUG_INTEGER) return WHITE;
        return BLUE; // TRACE
    }
}
