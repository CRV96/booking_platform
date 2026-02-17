package com.booking.platform.event_service.messaging.serializer;

import com.google.protobuf.MessageLite;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Kafka value serializer for Protobuf messages.
 *
 * <p>Spring Kafka does not ship a Protobuf serializer out of the box, so we
 * implement one ourselves. It is intentionally minimal: Protobuf's own
 * {@link MessageLite#toByteArray()} handles all encoding — we just delegate
 * to it and let Kafka transport the resulting byte array.
 *
 * <p>Registered in {@code config/dev/event-service.properties} as:
 * <pre>
 * spring.kafka.producer.value-serializer=
 *     com.booking.platform.event_service.kafka.serializer.ProtobufSerializer
 * </pre>
 *
 * <p>On the consumer side (notification-service, analytics-service) the
 * matching deserializer will call {@code MessageLite.parseFrom(bytes)} with
 * the concrete message type, restoring the full object.
 */
public class ProtobufSerializer implements Serializer<MessageLite> {

    /**
     * Serializes a Protobuf message to its binary wire format.
     *
     * @param topic   Kafka topic name (unused — serialization is type-agnostic)
     * @param message the Protobuf message to encode; {@code null} is safe and
     *                produces a {@code null} byte array (Kafka skips null values)
     * @return binary-encoded Protobuf bytes, or {@code null} if message is null
     */
    @Override
    public byte[] serialize(String topic, MessageLite message) {
        if (message == null) {
            return null;
        }
        return message.toByteArray();
    }
}
