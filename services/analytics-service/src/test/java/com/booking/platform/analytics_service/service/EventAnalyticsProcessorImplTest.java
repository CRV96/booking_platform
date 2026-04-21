package com.booking.platform.analytics_service.service;

import com.booking.platform.analytics_service.constants.AnalyticsConstants;
import com.booking.platform.analytics_service.document.EventLog;
import com.booking.platform.analytics_service.dto.EventDto;
import com.booking.platform.analytics_service.repository.EventLogRepository;
import com.booking.platform.analytics_service.service.impl.EventAnalyticsProcessorImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventAnalyticsProcessorImplTest {

    @Mock private EventLogRepository eventLogRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private EventAnalyticsProcessorImpl processor;

    private EventDto eventCreated() {
        return EventDto.builder()
                .topic("event.created").key("ev-1")
                .eventId("ev-1").title("Rock Fest").category("CONCERT")
                .build();
    }

    private EventDto eventUpdated() {
        return EventDto.builder()
                .topic("event.updated").key("ev-1")
                .eventId("ev-1").changedFields(List.of("title", "description"))
                .build();
    }

    private EventDto eventPublished() {
        return EventDto.builder()
                .topic("event.published").key("ev-1")
                .eventId("ev-1").title("Rock Fest").category("CONCERT")
                .build();
    }

    private EventDto eventCancelled() {
        return EventDto.builder()
                .topic("event.cancelled").key("ev-1")
                .eventId("ev-1").reason("Venue unavailable")
                .build();
    }

    // ── processEventCreated ───────────────────────────────────────────────────

    @Test
    void processEventCreated_savesRawEvent() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        processor.processEventCreated(eventCreated());

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AnalyticsConstants.Event.CREATED_EVENT);
        assertThat(captor.getValue().getPayload()).containsKey(AnalyticsConstants.PAYLOAD_EVENT_ID);
        assertThat(captor.getValue().getPayload()).containsKey(AnalyticsConstants.Event.PAYLOAD_TITLE);
    }

    @Test
    void processEventCreated_upsertsEventStats() {
        processor.processEventCreated(eventCreated());

        verify(mongoTemplate).upsert(any(), any(),
                eq(AnalyticsConstants.Collection.EVENT_STATS));
    }

    @Test
    void processEventCreated_upsertsDailyMetricsEventsCreated() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processEventCreated(eventCreated());

        verify(mongoTemplate).upsert(any(), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.DAILY_METRICS));
        assertThat(updateCaptor.getValue().toString())
                .contains(AnalyticsConstants.Event.EVENTS_CREATED);
    }

    @Test
    void processEventCreated_upsertsCategoryStats() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processEventCreated(eventCreated());

        verify(mongoTemplate).upsert(any(), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.CATEGORY_STATS));
        assertThat(updateCaptor.getValue().toString())
                .contains(AnalyticsConstants.Event.TOTAL_EVENTS);
    }

    // ── processEventUpdated ───────────────────────────────────────────────────

    @Test
    void processEventUpdated_savesRawEventWithChangedFields() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        processor.processEventUpdated(eventUpdated());

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AnalyticsConstants.Event.UPDATED_EVENT);
        assertThat(captor.getValue().getPayload()).containsKey(AnalyticsConstants.Event.PAYLOAD_CHANGED_FIELDS);
    }

    @Test
    void processEventUpdated_doesNotUpdateMaterializedViews() {
        processor.processEventUpdated(eventUpdated());

        // Updated events only go to the raw log — no mongo upserts
        verifyNoInteractions(mongoTemplate);
    }

    // ── processEventPublished ─────────────────────────────────────────────────

    @Test
    void processEventPublished_savesRawEvent() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        processor.processEventPublished(eventPublished());

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AnalyticsConstants.Event.PUBLISHED_EVENT);
    }

    @Test
    void processEventPublished_upsertsDailyMetricsEventsPublished() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processEventPublished(eventPublished());

        verify(mongoTemplate).upsert(any(), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.DAILY_METRICS));
        assertThat(updateCaptor.getValue().toString())
                .contains(AnalyticsConstants.Event.EVENTS_PUBLISHED);
    }

    @Test
    void processEventPublished_upsertsCategoryStatsPublishedEvents() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processEventPublished(eventPublished());

        verify(mongoTemplate).upsert(any(), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.CATEGORY_STATS));
        assertThat(updateCaptor.getValue().toString())
                .contains(AnalyticsConstants.Event.PUBLISHED_EVENTS);
    }

    // ── processEventCancelled ─────────────────────────────────────────────────

    @Test
    void processEventCancelled_savesRawEventWithReason() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        processor.processEventCancelled(eventCancelled());

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AnalyticsConstants.Event.CANCELLED_EVENT);
        assertThat(captor.getValue().getPayload()).containsEntry(
                AnalyticsConstants.PAYLOAD_REASON, "Venue unavailable");
    }

    @Test
    void processEventCancelled_upsertsDailyMetricsEventsCancelled() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processEventCancelled(eventCancelled());

        verify(mongoTemplate).upsert(any(), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.DAILY_METRICS));
        assertThat(updateCaptor.getValue().toString())
                .contains(AnalyticsConstants.Event.EVENTS_CANCELLED);
    }

    @Test
    void processEventCancelled_doesNotUpdateCategoryStats() {
        processor.processEventCancelled(eventCancelled());

        verify(mongoTemplate, never()).upsert(any(), any(),
                eq(AnalyticsConstants.Collection.CATEGORY_STATS));
    }
}
