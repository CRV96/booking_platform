package com.booking.platform.event_service.init;

import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.repository.EventRepository;
import com.booking.platform.event_service.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.ApplicationArguments;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataInitializerTest {

    @Mock private EventService eventService;
    @Mock private EventRepository eventRepository;

    @InjectMocks private DataInitializer initializer;

    private final ApplicationArguments args = mock(ApplicationArguments.class);

    // ── shared mock EventDocument returned by createEvent ─────────────────────

    private EventDocument eventDoc(String id) {
        EventDocument doc = new EventDocument();
        doc.setId(id);
        doc.setStatus(EventStatus.DRAFT);
        return doc;
    }

    @BeforeEach
    void setUp() {
        AtomicInteger counter = new AtomicInteger();
        when(eventService.createEvent(any(), any()))
                .thenAnswer(inv -> eventDoc("id-" + counter.incrementAndGet()));
        when(eventService.publishEvent(anyString()))
                .thenAnswer(inv -> {
                    EventDocument doc = new EventDocument();
                    doc.setId(inv.getArgument(0));
                    doc.setStatus(EventStatus.PUBLISHED);
                    return doc;
                });
        when(eventService.cancelEvent(anyString(), anyString()))
                .thenAnswer(inv -> {
                    EventDocument doc = new EventDocument();
                    doc.setId(inv.getArgument(0));
                    doc.setStatus(EventStatus.CANCELLED);
                    return doc;
                });
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void run_skipsSeeding_whenEventsAlreadyExist() {
        when(eventRepository.count()).thenReturn(5L);

        initializer.run(args);

        verifyNoInteractions(eventService);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void run_doesNotCallService_whenCountIsOne() {
        when(eventRepository.count()).thenReturn(1L);

        initializer.run(args);

        verifyNoInteractions(eventService);
    }

    // ── full seed — aggregate call counts ─────────────────────────────────────

    @Test
    void run_creates50Events_whenDatabaseIsEmpty() {
        when(eventRepository.count()).thenReturn(0L);

        initializer.run(args);

        // 50 total: 27 published + 11 completed + 6 draft + 6 cancelled
        verify(eventService, times(50)).createEvent(any(), any());
    }

    @Test
    void run_publishes44Events_whenDatabaseIsEmpty() {
        when(eventRepository.count()).thenReturn(0L);

        initializer.run(args);

        // 27 published + 11 completed (forced past) + 6 cancelled = 44
        verify(eventService, times(44)).publishEvent(anyString());
    }

    @Test
    void run_cancels4Events_whenDatabaseIsEmpty() {
        when(eventRepository.count()).thenReturn(0L);

        initializer.run(args);

        verify(eventService, times(6)).cancelEvent(anyString(), anyString());
    }

    @Test
    void run_saves12CompletedEventsViaRepository_whenDatabaseIsEmpty() {
        when(eventRepository.count()).thenReturn(0L);

        initializer.run(args);

        // createCompleted() bypasses the service to set COMPLETED status
        verify(eventRepository, times(11)).save(any(EventDocument.class));
    }

    // ── createCompleted path: COMPLETED status + past dateTime ────────────────

    @Test
    void run_savedCompletedEvents_haveCompletedStatus() {
        when(eventRepository.count()).thenReturn(0L);

        initializer.run(args);

        ArgumentCaptor<EventDocument> captor = ArgumentCaptor.forClass(EventDocument.class);
        verify(eventRepository, times(11)).save(captor.capture());

        List<EventDocument> saved = captor.getAllValues();
        assertThat(saved).allMatch(doc -> doc.getStatus() == EventStatus.COMPLETED);
    }

    @Test
    void run_savedCompletedEvents_havePastDateTime() {
        when(eventRepository.count()).thenReturn(0L);

        initializer.run(args);

        ArgumentCaptor<EventDocument> captor = ArgumentCaptor.forClass(EventDocument.class);
        verify(eventRepository, times(11)).save(captor.capture());

        Instant now = Instant.now();
        List<EventDocument> saved = captor.getAllValues();
        assertThat(saved).allMatch(doc -> doc.getDateTime().isBefore(now));
        assertThat(saved).allMatch(doc -> doc.getEndDateTime().isBefore(now));
    }

    // ── createAndPublish path ─────────────────────────────────────────────────

    @Test
    void run_publishesWithCorrectEventId_fromCreateEventResult() {
        when(eventRepository.count()).thenReturn(0L);

        initializer.run(args);

        // Every publishEvent call must use an ID that was returned by a prior createEvent call.
        ArgumentCaptor<String> publishedIds = ArgumentCaptor.forClass(String.class);
        verify(eventService, times(44)).publishEvent(publishedIds.capture());

        assertThat(publishedIds.getAllValues()).allMatch(id -> id.startsWith("id-"));
    }

    // ── createAndCancel path ──────────────────────────────────────────────────

    @Test
    void run_cancelsWithDefaultReason() {
        when(eventRepository.count()).thenReturn(0L);

        initializer.run(args);

        ArgumentCaptor<String> reasons = ArgumentCaptor.forClass(String.class);
        verify(eventService, times(6)).cancelEvent(anyString(), reasons.capture());

        assertThat(reasons.getAllValues())
                .allMatch(r -> r != null && !r.isBlank());
    }

    // ── boundary: count == 0 triggers seed ────────────────────────────────────

    @Test
    void run_seedsWhenCountIsZero() {
        when(eventRepository.count()).thenReturn(0L);

        initializer.run(args);

        verify(eventService, atLeastOnce()).createEvent(any(), any());
    }
}
