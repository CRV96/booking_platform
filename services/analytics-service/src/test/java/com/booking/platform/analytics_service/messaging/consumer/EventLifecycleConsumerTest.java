package com.booking.platform.analytics_service.messaging.consumer;

import com.booking.platform.analytics_service.dto.EventDto;
import com.booking.platform.analytics_service.service.EventAnalyticsProcessor;
import com.booking.platform.common.events.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventLifecycleConsumerTest {

    @Mock private EventAnalyticsProcessor processor;

    @InjectMocks private EventLifecycleConsumer consumer;

    private <V> ConsumerRecord<String, V> record(String topic, String key, V value) {
        return new ConsumerRecord<>(topic, 0, 0L, key, value);
    }

    // ── onEventCreated ────────────────────────────────────────────────────────

    @Test
    void onEventCreated_buildsCorrectDto() {
        EventCreatedEvent event = EventCreatedEvent.newBuilder()
                .setEventId("ev-1").setTitle("Rock Fest").setCategory("CONCERT")
                .build();

        consumer.onEventCreated(record("event.created", "ev-1", event));

        ArgumentCaptor<EventDto> captor = ArgumentCaptor.forClass(EventDto.class);
        verify(processor).processEventCreated(captor.capture());

        EventDto dto = captor.getValue();
        assertThat(dto.eventId()).isEqualTo("ev-1");
        assertThat(dto.title()).isEqualTo("Rock Fest");
        assertThat(dto.category()).isEqualTo("CONCERT");
        assertThat(dto.topic()).isEqualTo("event.created");
        assertThat(dto.key()).isEqualTo("ev-1");
    }

    // ── onEventUpdated ────────────────────────────────────────────────────────

    @Test
    void onEventUpdated_buildsCorrectDtoWithChangedFields() {
        EventUpdatedEvent event = EventUpdatedEvent.newBuilder()
                .setEventId("ev-2")
                .addChangedFields("title")
                .addChangedFields("description")
                .build();

        consumer.onEventUpdated(record("event.updated", "ev-2", event));

        ArgumentCaptor<EventDto> captor = ArgumentCaptor.forClass(EventDto.class);
        verify(processor).processEventUpdated(captor.capture());

        EventDto dto = captor.getValue();
        assertThat(dto.eventId()).isEqualTo("ev-2");
        assertThat(dto.changedFields()).containsExactly("title", "description");
    }

    // ── onEventPublished ──────────────────────────────────────────────────────

    @Test
    void onEventPublished_buildsCorrectDto() {
        EventPublishedEvent event = EventPublishedEvent.newBuilder()
                .setEventId("ev-3").setTitle("Jazz Night").setCategory("MUSIC")
                .build();

        consumer.onEventPublished(record("event.published", "ev-3", event));

        ArgumentCaptor<EventDto> captor = ArgumentCaptor.forClass(EventDto.class);
        verify(processor).processEventPublished(captor.capture());

        EventDto dto = captor.getValue();
        assertThat(dto.eventId()).isEqualTo("ev-3");
        assertThat(dto.category()).isEqualTo("MUSIC");
    }

    // ── onEventCancelled ──────────────────────────────────────────────────────

    @Test
    void onEventCancelled_buildsCorrectDtoWithReason() {
        EventCancelledEvent event = EventCancelledEvent.newBuilder()
                .setEventId("ev-4").setReason("Venue unavailable")
                .build();

        consumer.onEventCancelled(record("event.cancelled", "ev-4", event));

        ArgumentCaptor<EventDto> captor = ArgumentCaptor.forClass(EventDto.class);
        verify(processor).processEventCancelled(captor.capture());

        EventDto dto = captor.getValue();
        assertThat(dto.eventId()).isEqualTo("ev-4");
        assertThat(dto.reason()).isEqualTo("Venue unavailable");
    }
}
