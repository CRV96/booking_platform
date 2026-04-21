package com.booking.platform.analytics_service.service;

import com.booking.platform.analytics_service.constants.AnalyticsConstants;
import com.booking.platform.analytics_service.document.EventLog;
import com.booking.platform.analytics_service.dto.BookingDto;
import com.booking.platform.analytics_service.repository.EventLogRepository;
import com.booking.platform.analytics_service.service.impl.BookingAnalyticsProcessorImpl;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingAnalyticsProcessorImplTest {

    @Mock private EventLogRepository eventLogRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private BookingAnalyticsProcessorImpl processor;

    private BookingDto bookingCreated() {
        return BookingDto.builder()
                .topic("booking.created").key("bk-1")
                .bookingId("bk-1").eventId("ev-1")
                .totalPrice(100.0).currency("USD")
                .build();
    }

    private BookingDto bookingConfirmed() {
        return BookingDto.builder()
                .topic("booking.confirmed").key("bk-1")
                .bookingId("bk-1").eventId("ev-1")
                .totalPrice(100.0).currency("USD")
                .eventTitle("Rock Fest").seatCategory("VIP")
                .build();
    }

    private BookingDto bookingCancelled() {
        return BookingDto.builder()
                .topic("booking.cancelled").key("bk-1")
                .bookingId("bk-1").eventId("ev-1")
                .reason("changed mind")
                .build();
    }

    // ── processBookingCreated ─────────────────────────────────────────────────

    @Test
    void processBookingCreated_savesRawEventLog() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        processor.processBookingCreated(bookingCreated());

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AnalyticsConstants.Booking.CREATED_EVENT);
        assertThat(captor.getValue().getPayload()).containsKey(AnalyticsConstants.PAYLOAD_BOOKING_ID);
        assertThat(captor.getValue().getPayload()).containsKey(AnalyticsConstants.PAYLOAD_EVENT_ID);
    }

    @Test
    void processBookingCreated_upsertsEventStats() {
        processor.processBookingCreated(bookingCreated());

        verify(mongoTemplate).upsert(any(Query.class), any(Update.class),
                eq(AnalyticsConstants.Collection.EVENT_STATS));
    }

    @Test
    void processBookingCreated_upsertsDailyMetrics() {
        processor.processBookingCreated(bookingCreated());

        verify(mongoTemplate).upsert(any(Query.class), any(Update.class),
                eq(AnalyticsConstants.Collection.DAILY_METRICS));
    }

    @Test
    void processBookingCreated_lookUpCategoryForCategoryStats() {
        // eventStats not found — category_stats should be skipped
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class),
                eq(AnalyticsConstants.Collection.EVENT_STATS))).thenReturn(null);

        processor.processBookingCreated(bookingCreated());

        // category_stats should NOT be upserted when event_stats not found
        verify(mongoTemplate, never()).upsert(any(), any(),
                eq(AnalyticsConstants.Collection.CATEGORY_STATS));
    }

    @Test
    void processBookingCreated_categoryStatsUpdatedWhenEventStatsFound() {
        Document eventStats = new Document("eventId", "ev-1").append("category", "CONCERT");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class),
                eq(AnalyticsConstants.Collection.EVENT_STATS))).thenReturn(eventStats);

        processor.processBookingCreated(bookingCreated());

        verify(mongoTemplate, atLeastOnce()).upsert(any(), any(),
                eq(AnalyticsConstants.Collection.CATEGORY_STATS));
    }

    // ── processBookingConfirmed ───────────────────────────────────────────────

    @Test
    void processBookingConfirmed_savesRawEventLog() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        processor.processBookingConfirmed(bookingConfirmed());

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AnalyticsConstants.Booking.CONFIRMED_EVENT);
        assertThat(captor.getValue().getPayload()).containsKey(AnalyticsConstants.PAYLOAD_EVENT_TITLE);
    }

    @Test
    void processBookingConfirmed_upsertsEventStatsWithRevenue() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processBookingConfirmed(bookingConfirmed());

        verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.EVENT_STATS));
        // The update document should reference confirmedBookings and totalRevenue
        String updateStr = updateCaptor.getValue().toString();
        assertThat(updateStr).contains(AnalyticsConstants.Booking.CONFIRMED_BOOKINGS);
        assertThat(updateStr).contains(AnalyticsConstants.Booking.TOTAL_REVENUE);
    }

    @Test
    void processBookingConfirmed_upsertsDailyMetricsWithRevenue() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processBookingConfirmed(bookingConfirmed());

        verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.DAILY_METRICS));
        String updateStr = updateCaptor.getValue().toString();
        assertThat(updateStr).contains(AnalyticsConstants.Booking.TOTAL_REVENUE);
    }

    @Test
    void processBookingConfirmed_categoryStatsUpdatedWithRevenue() {
        Document eventStats = new Document("eventId", "ev-1").append("category", "CONCERT");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class),
                eq(AnalyticsConstants.Collection.EVENT_STATS))).thenReturn(eventStats);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processBookingConfirmed(bookingConfirmed());

        verify(mongoTemplate, atLeastOnce()).upsert(any(), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.CATEGORY_STATS));
        String updateStr = updateCaptor.getAllValues().stream()
                .map(Object::toString).reduce("", String::concat);
        assertThat(updateStr).contains(AnalyticsConstants.Booking.TOTAL_REVENUE);
    }

    // ── processBookingCancelled ───────────────────────────────────────────────

    @Test
    void processBookingCancelled_savesRawEventLogWithReason() {
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        processor.processBookingCancelled(bookingCancelled());

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AnalyticsConstants.Booking.CANCELLED_EVENT);
        assertThat(captor.getValue().getPayload()).containsEntry(
                AnalyticsConstants.PAYLOAD_REASON, "changed mind");
    }

    @Test
    void processBookingCancelled_upsertsEventStatsWithCancelledCount() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processBookingCancelled(bookingCancelled());

        verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.EVENT_STATS));
        assertThat(updateCaptor.getValue().toString())
                .contains(AnalyticsConstants.Booking.CANCELLED_BOOKINGS);
    }

    @Test
    void processBookingCancelled_upsertsDailyMetrics() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        processor.processBookingCancelled(bookingCancelled());

        verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(),
                eq(AnalyticsConstants.Collection.DAILY_METRICS));
        assertThat(updateCaptor.getValue().toString())
                .contains(AnalyticsConstants.Booking.BOOKINGS_CANCELLED);
    }

    @Test
    void processBookingCancelled_doesNotUpdateCategoryStats() {
        processor.processBookingCancelled(bookingCancelled());

        // Cancelled only updates daily_metrics + event_stats — no category_stats
        verify(mongoTemplate, never()).upsert(any(), any(),
                eq(AnalyticsConstants.Collection.CATEGORY_STATS));
    }
}
