package com.booking.platform.user_service.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationPropertiesTest {

    @Test
    void nullEmailPattern_appliesDefault() {
        ValidationProperties props = new ValidationProperties(null, 8, 128, 100, 255, 1, 100);
        assertThat(props.emailPattern()).isNotBlank().contains("@");
    }

    @Test
    void blankEmailPattern_appliesDefault() {
        ValidationProperties props = new ValidationProperties("   ", 8, 128, 100, 255, 1, 100);
        assertThat(props.emailPattern()).isNotBlank();
    }

    @Test
    void zeroMinPasswordLength_defaultsToEight() {
        assertThat(new ValidationProperties(null, 0, 128, 100, 255, 1, 100).minPasswordLength())
                .isEqualTo(8);
    }

    @Test
    void zeroMaxPasswordLength_defaultsTo128() {
        assertThat(new ValidationProperties(null, 8, 0, 100, 255, 1, 100).maxPasswordLength())
                .isEqualTo(128);
    }

    @Test
    void zeroMaxNameLength_defaultsTo100() {
        assertThat(new ValidationProperties(null, 8, 128, 0, 255, 1, 100).maxNameLength())
                .isEqualTo(100);
    }

    @Test
    void zeroMaxEmailLength_defaultsTo255() {
        assertThat(new ValidationProperties(null, 8, 128, 100, 0, 1, 100).maxEmailLength())
                .isEqualTo(255);
    }

    @Test
    void zeroMinPageSize_defaultsToOne() {
        assertThat(new ValidationProperties(null, 8, 128, 100, 255, 0, 100).minPageSize())
                .isEqualTo(1);
    }

    @Test
    void zeroMaxPageSize_defaultsTo100() {
        assertThat(new ValidationProperties(null, 8, 128, 100, 255, 1, 0).maxPageSize())
                .isEqualTo(100);
    }

    @Test
    void explicitValues_arePreserved() {
        ValidationProperties props = new ValidationProperties("^\\S+@\\S+$", 6, 64, 50, 100, 5, 50);
        assertThat(props.emailPattern()).isEqualTo("^\\S+@\\S+$");
        assertThat(props.minPasswordLength()).isEqualTo(6);
        assertThat(props.maxPasswordLength()).isEqualTo(64);
        assertThat(props.maxNameLength()).isEqualTo(50);
        assertThat(props.maxEmailLength()).isEqualTo(100);
        assertThat(props.minPageSize()).isEqualTo(5);
        assertThat(props.maxPageSize()).isEqualTo(50);
    }
}
