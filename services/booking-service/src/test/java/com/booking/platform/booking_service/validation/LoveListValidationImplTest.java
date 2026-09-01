package com.booking.platform.booking_service.validation;

import com.booking.platform.booking_service.validation.impl.LoveListValidationImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoveListValidationImplTest {

    private final LoveListValidationImpl validation = new LoveListValidationImpl();

    @Test
    void validateEventId_present_passes() {
        assertThatCode(() -> validation.validateEventId("event-1")).doesNotThrowAnyException();
    }

    @Test
    void validateEventId_null_throws() {
        assertThatThrownBy(() -> validation.validateEventId(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("event_id");
    }

    @Test
    void validateEventId_blank_throws() {
        assertThatThrownBy(() -> validation.validateEventId("  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("event_id");
    }
}
