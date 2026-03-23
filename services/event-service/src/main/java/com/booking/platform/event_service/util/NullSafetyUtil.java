package com.booking.platform.event_service.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.function.Function;

/**
 * Utility methods for event processing, such as null handling and Instant formatting.
 */
@Slf4j
public final class NullSafetyUtil {

    /** Private constructor to prevent instantiation */
    private NullSafetyUtil() {}

    /** Converts an Instant to its ISO-8601 string representation, or returns an empty string if null. */
    public static String instantToString(Instant instant) {
        return instant != null ? instant.toString() : "";
    }

    /** Converts a string to an Instant, or returns null if the string is null or blank. */
    public static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

        /** Extracts a string from an object using the provided extractor function, or returns an empty string if the object is null. */
    public static <T> String nullToEmpty(T obj, Function<T, String> extractor) {
        return obj != null ? nullToEmpty(extractor.apply(obj)) : "";
    }

    /** Converts a value to itself if not null, or returns 0 (or 0.0) if null. */
    public static double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    /** Converts a value to itself if not null, or returns 0 if null. */
    public static int orZero(Integer value) {
        return value != null ? value : 0;
    }

}
