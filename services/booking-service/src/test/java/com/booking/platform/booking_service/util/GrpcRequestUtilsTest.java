package com.booking.platform.booking_service.util;

import com.booking.platform.common.grpc.context.GrpcUserContext;
import io.grpc.Context;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcRequestUtilsTest {

    // ── requireUserId ─────────────────────────────────────────────────────────

    @Test
    void requireUserId_present_returnsUserId() {
        Context ctx = Context.current().withValue(GrpcUserContext.USER_ID, "user-1");
        Context previous = ctx.attach();
        try {
            assertThat(GrpcRequestUtils.requireUserId()).isEqualTo("user-1");
        } finally {
            ctx.detach(previous);
        }
    }

    @Test
    void requireUserId_missing_throws() {
        assertThatThrownBy(GrpcRequestUtils::requireUserId)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user");
    }

    // ── parseUuid ─────────────────────────────────────────────────────────────

    @Test
    void parseUuid_valid_returnsUuid() {
        UUID id = UUID.randomUUID();
        assertThat(GrpcRequestUtils.parseUuid(id.toString(), "cart_item_id")).isEqualTo(id);
    }

    @Test
    void parseUuid_blank_throws() {
        assertThatThrownBy(() -> GrpcRequestUtils.parseUuid("  ", "cart_item_id"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cart_item_id");
    }

    @Test
    void parseUuid_malformed_throws() {
        assertThatThrownBy(() -> GrpcRequestUtils.parseUuid("not-a-uuid", "cart_item_id"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cart_item_id");
    }

    // ── parseDecimal ──────────────────────────────────────────────────────────

    @Test
    void parseDecimal_valid_returnsBigDecimal() {
        assertThat(GrpcRequestUtils.parseDecimal("49.99", "unit_price"))
                .isEqualByComparingTo(new BigDecimal("49.99"));
    }

    @Test
    void parseDecimal_blank_throws() {
        assertThatThrownBy(() -> GrpcRequestUtils.parseDecimal("", "unit_price"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit_price");
    }

    @Test
    void parseDecimal_malformed_throws() {
        assertThatThrownBy(() -> GrpcRequestUtils.parseDecimal("abc", "unit_price"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit_price");
    }
}
