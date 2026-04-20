package com.booking.platform.event_service.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NullSafetyUtilTest {

    // ── instantToString ───────────────────────────────────────────────────────

    @Test
    void instantToString_nonNull_returnsIsoString() {
        Instant instant = Instant.parse("2024-06-15T18:00:00Z");

        assertThat(NullSafetyUtil.instantToString(instant)).isEqualTo("2024-06-15T18:00:00Z");
    }

    @Test
    void instantToString_null_returnsEmptyString() {
        assertThat(NullSafetyUtil.instantToString(null)).isEmpty();
    }

    // ── nullToEmpty(String) ───────────────────────────────────────────────────

    @Test
    void nullToEmpty_nonNull_returnsValue() {
        assertThat(NullSafetyUtil.nullToEmpty("hello")).isEqualTo("hello");
    }

    @Test
    void nullToEmpty_null_returnsEmptyString() {
        assertThat(NullSafetyUtil.nullToEmpty((String) null)).isEmpty();
    }

    @Test
    void nullToEmpty_emptyString_returnsEmptyString() {
        assertThat(NullSafetyUtil.nullToEmpty("")).isEmpty();
    }

    // ── nullToEmpty(T, extractor) ─────────────────────────────────────────────

    @Test
    void nullToEmpty_nonNullObject_appliesExtractor() {
        assertThat(NullSafetyUtil.nullToEmpty("hello", String::toUpperCase)).isEqualTo("HELLO");
    }

    @Test
    void nullToEmpty_nullObject_returnsEmptyString() {
        assertThat(NullSafetyUtil.nullToEmpty((String) null, String::toUpperCase)).isEmpty();
    }

    @Test
    void nullToEmpty_extractorReturnsNull_returnsEmptyString() {
        assertThat(NullSafetyUtil.nullToEmpty("anything", s -> null)).isEmpty();
    }

    // ── orZero(Double) ────────────────────────────────────────────────────────

    @Test
    void orZeroDouble_nonNull_returnsValue() {
        assertThat(NullSafetyUtil.orZero(3.14)).isEqualTo(3.14);
    }

    @Test
    void orZeroDouble_null_returnsZero() {
        assertThat(NullSafetyUtil.orZero((Double) null)).isEqualTo(0.0);
    }

    // ── orZero(Integer) ───────────────────────────────────────────────────────

    @Test
    void orZeroInt_nonNull_returnsValue() {
        assertThat(NullSafetyUtil.orZero(42)).isEqualTo(42);
    }

    @Test
    void orZeroInt_null_returnsZero() {
        assertThat(NullSafetyUtil.orZero((Integer) null)).isEqualTo(0);
    }
}
