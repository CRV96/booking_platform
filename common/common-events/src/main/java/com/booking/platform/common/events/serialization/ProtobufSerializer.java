package com.booking.platform.common.events.serialization;

import com.google.protobuf.MessageLite;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Kafka value serializer for Protobuf messages.
 *
 * <p>Converts any {@link MessageLite} to its binary wire format using
 * {@link MessageLite#toByteArray()}. This is the counterpart to
 * {@link ProtobufDeserializer} — together they form a symmetric pair for
 * producing and consuming Protobuf messages over Kafka.
 *
 * <p>Usage in {@code KafkaProducerConfig}:
 * <pre>
 * config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ProtobufSerializer.class);
 * </pre>
 */
public class ProtobufSerializer implements Serializer<MessageLite> {

    @Override
    public byte[] serialize(String topic, MessageLite message) {
        if (message == null) {
            return null;
        }
        return message.toByteArray();
    }
}
