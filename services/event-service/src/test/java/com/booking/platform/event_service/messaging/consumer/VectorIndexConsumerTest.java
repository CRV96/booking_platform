package com.booking.platform.event_service.messaging.consumer;

import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.event_service.service.EventVectorIndexer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VectorIndexConsumerTest {

    @Mock private EventVectorIndexer indexer;
    @InjectMocks private VectorIndexConsumer consumer;

    private <T> ConsumerRecord<String, T> record(T value) {
        return new ConsumerRecord<>("topic", 0, 0L, "key", value);
    }

    @Test
    void onEventCreated_indexesEvent() {
        consumer.onEventCreated(record(EventCreatedEvent.newBuilder().setEventId("e1").build()));
        verify(indexer).index("e1");
    }

    @Test
    void onEventUpdated_indexesEvent() {
        consumer.onEventUpdated(record(EventUpdatedEvent.newBuilder().setEventId("e2").build()));
        verify(indexer).index("e2");
    }

    @Test
    void onEventPublished_indexesEvent() {
        consumer.onEventPublished(record(EventPublishedEvent.newBuilder().setEventId("e3").build()));
        verify(indexer).index("e3");
    }

    @Test
    void onEventCancelled_removesEvent() {
        consumer.onEventCancelled(record(EventCancelledEvent.newBuilder().setEventId("e4").build()));
        verify(indexer).remove("e4");
    }
}
