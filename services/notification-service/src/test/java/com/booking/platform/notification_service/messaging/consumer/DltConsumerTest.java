package com.booking.platform.notification_service.messaging.consumer;

import com.booking.platform.common.events.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for {@link DltConsumer}.
 *
 * <p>DLT consumers are pure logging — they have no side effects to assert.
 * These tests verify that:
 * <ul>
 *   <li>Valid Protobuf bytes are deserialized without exception</li>
 *   <li>Invalid (poison-pill) bytes fall back to raw-byte logging without throwing</li>
 * </ul>
 * Any exception propagating from a DLT listener would cause Kafka to retry or
 * route the message to a second-level DLT, which would defeat the purpose of DLT processing.
 */
class DltConsumerTest {

    private final DltConsumer consumer = new DltConsumer();

    // ── Event DLTs ────────────────────────────────────────────────────────────

    @Test
    void onEventCreatedDlt_validProtobuf_doesNotThrow() {
        byte[] bytes = EventCreatedEvent.newBuilder()
                .setEventId("ev-1").setTitle("Fest").setCategory("MUSIC")
                .setOrganizerId("org-1").setTimestamp("2024-01-01T00:00:00Z")
                .build().toByteArray();
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.event.created-dlt", 0, 1L, "key", bytes);

        assertDoesNotThrow(() -> consumer.onEventCreatedDlt(record));
    }

    @Test
    void onEventCreatedDlt_poisonPill_doesNotThrow() {
        byte[] garbage = new byte[]{0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.event.created-dlt", 0, 1L, "key", garbage);

        assertDoesNotThrow(() -> consumer.onEventCreatedDlt(record));
    }

    @Test
    void onEventUpdatedDlt_validProtobuf_doesNotThrow() {
        byte[] bytes = EventUpdatedEvent.newBuilder()
                .setEventId("ev-2").addChangedFields("dateTime").setTimestamp("2024-01-01T00:00:00Z")
                .build().toByteArray();
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.event.updated-dlt", 0, 1L, "key", bytes);

        assertDoesNotThrow(() -> consumer.onEventUpdatedDlt(record));
    }

    @Test
    void onEventUpdatedDlt_poisonPill_doesNotThrow() {
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.event.updated-dlt", 0, 1L, "key", new byte[]{(byte) 0xAB});

        assertDoesNotThrow(() -> consumer.onEventUpdatedDlt(record));
    }

    @Test
    void onEventPublishedDlt_validProtobuf_doesNotThrow() {
        byte[] bytes = EventPublishedEvent.newBuilder()
                .setEventId("ev-3").setTitle("Live").setCategory("MUSIC")
                .setOrganizerId("org-1").setDateTime("2024-07-01T18:00:00Z")
                .setTimestamp("2024-01-01T00:00:00Z")
                .build().toByteArray();
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.event.published-dlt", 0, 1L, "key", bytes);

        assertDoesNotThrow(() -> consumer.onEventPublishedDlt(record));
    }

    @Test
    void onEventPublishedDlt_poisonPill_doesNotThrow() {
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.event.published-dlt", 0, 1L, "key", new byte[]{0x00});

        assertDoesNotThrow(() -> consumer.onEventPublishedDlt(record));
    }

    @Test
    void onEventCancelledDlt_validProtobuf_doesNotThrow() {
        byte[] bytes = EventCancelledEvent.newBuilder()
                .setEventId("ev-4").setReason("Flooded").setTimestamp("2024-01-01T00:00:00Z")
                .build().toByteArray();
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.event.cancelled-dlt", 0, 1L, "key", bytes);

        assertDoesNotThrow(() -> consumer.onEventCancelledDlt(record));
    }

    @Test
    void onEventCancelledDlt_poisonPill_doesNotThrow() {
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.event.cancelled-dlt", 0, 1L, "key", new byte[]{(byte) 0xDE, (byte) 0xAD});

        assertDoesNotThrow(() -> consumer.onEventCancelledDlt(record));
    }

    // ── Booking DLTs ──────────────────────────────────────────────────────────

    @Test
    void onBookingCreatedDlt_validProtobuf_doesNotThrow() {
        byte[] bytes = BookingCreatedEvent.newBuilder()
                .setBookingId("bk-1").setUserId("u-1").setEventId("ev-1")
                .setTimestamp("2024-01-01T00:00:00Z")
                .build().toByteArray();
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.booking.created-dlt", 0, 1L, "key", bytes);

        assertDoesNotThrow(() -> consumer.onBookingCreatedDlt(record));
    }

    @Test
    void onBookingCreatedDlt_poisonPill_doesNotThrow() {
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.booking.created-dlt", 0, 1L, "key", new byte[]{0x01});

        assertDoesNotThrow(() -> consumer.onBookingCreatedDlt(record));
    }

    @Test
    void onBookingConfirmedDlt_validProtobuf_doesNotThrow() {
        byte[] bytes = BookingConfirmedEvent.newBuilder()
                .setBookingId("bk-2").setUserId("u-2").setEventId("ev-1")
                .addTicketIds("t-1").setTimestamp("2024-01-01T00:00:00Z")
                .build().toByteArray();
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.booking.confirmed-dlt", 0, 1L, "key", bytes);

        assertDoesNotThrow(() -> consumer.onBookingConfirmedDlt(record));
    }

    @Test
    void onBookingConfirmedDlt_poisonPill_doesNotThrow() {
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.booking.confirmed-dlt", 0, 1L, "key", new byte[]{(byte) 0xFF});

        assertDoesNotThrow(() -> consumer.onBookingConfirmedDlt(record));
    }

    @Test
    void onBookingCancelledDlt_validProtobuf_doesNotThrow() {
        byte[] bytes = BookingCancelledEvent.newBuilder()
                .setBookingId("bk-3").setUserId("u-3").setEventId("ev-1")
                .setReason("User request").setTimestamp("2024-01-01T00:00:00Z")
                .build().toByteArray();
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.booking.cancelled-dlt", 0, 1L, "key", bytes);

        assertDoesNotThrow(() -> consumer.onBookingCancelledDlt(record));
    }

    @Test
    void onBookingCancelledDlt_poisonPill_doesNotThrow() {
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.booking.cancelled-dlt", 0, 1L, "key", new byte[]{0x02, 0x03});

        assertDoesNotThrow(() -> consumer.onBookingCancelledDlt(record));
    }

    @Test
    void onPaymentFailedDlt_validProtobuf_doesNotThrow() {
        byte[] bytes = PaymentFailedEvent.newBuilder()
                .setPaymentId("pay-1").setBookingId("bk-1")
                .setReason("Card declined").setTimestamp("2024-01-01T00:00:00Z")
                .build().toByteArray();
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.payment.failed-dlt", 0, 1L, "key", bytes);

        assertDoesNotThrow(() -> consumer.onPaymentFailedDlt(record));
    }

    @Test
    void onPaymentFailedDlt_poisonPill_doesNotThrow() {
        ConsumerRecord<String, byte[]> record =
                new ConsumerRecord<>("events.payment.failed-dlt", 0, 1L, "key", new byte[]{0x00, (byte) 0xFF, 0x01});

        assertDoesNotThrow(() -> consumer.onPaymentFailedDlt(record));
    }

}
