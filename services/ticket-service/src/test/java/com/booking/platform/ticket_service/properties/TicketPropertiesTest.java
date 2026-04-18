package com.booking.platform.ticket_service.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketPropertiesTest {

    @Test
    void zeroQuantity_defaultsTo20() {
        assertThat(new TicketProperties(0).maxQuantityPerBooking()).isEqualTo(20);
    }

    @Test
    void negativeQuantity_defaultsTo20() {
        assertThat(new TicketProperties(-5).maxQuantityPerBooking()).isEqualTo(20);
    }

    @Test
    void positiveQuantity_preserved() {
        assertThat(new TicketProperties(50).maxQuantityPerBooking()).isEqualTo(50);
    }

    @Test
    void oneQuantity_preserved() {
        assertThat(new TicketProperties(1).maxQuantityPerBooking()).isEqualTo(1);
    }
}
