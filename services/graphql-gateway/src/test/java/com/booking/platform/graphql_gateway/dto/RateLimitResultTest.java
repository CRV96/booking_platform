package com.booking.platform.graphql_gateway.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitResultTest {

    @Test
    void remaining_countsBelow_returnsCorrectRemainder() {
        RateLimitResult result = new RateLimitResult(true, 7, 30, 0);

        assertThat(result.remaining()).isEqualTo(23);
    }

    @Test
    void remaining_countEqualsLimit_returnsZero() {
        RateLimitResult result = new RateLimitResult(true, 30, 30, 0);

        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void remaining_countExceedsLimit_clampsToZero() {
        RateLimitResult result = new RateLimitResult(false, 35, 30, 10);

        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void allowed_true_representsAllowedRequest() {
        RateLimitResult result = new RateLimitResult(true, 1, 30, 0);

        assertThat(result.allowed()).isTrue();
        assertThat(result.retryAfterSeconds()).isEqualTo(0);
    }

    @Test
    void allowed_false_representsBlockedRequest() {
        RateLimitResult result = new RateLimitResult(false, 35, 30, 45);

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(45);
    }
}
