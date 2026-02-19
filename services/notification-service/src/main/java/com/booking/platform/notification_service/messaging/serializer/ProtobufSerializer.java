package com.booking.platform.notification_service.messaging.serializer;

import com.google.protobuf.MessageLite;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Kafka value serializer for Protobuf messages.
 *
 * <p>Used exclusively by the {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer}
 * to forward failed messages to their Dead Letter Topics. The recoverer receives a
 * deserialized Protobuf object from the failed {@code ConsumerRecord} and re-publishes
 * it to {@code <original-topic>.DLT} — this serializer converts it back to binary
 * wire format for Kafka transport.
 *
 * <p>The DLT consumer ({@link com.booking.platform.notification_service.messaging.consumer.DltConsumer})
 * reads DLT messages as raw {@code byte[]} via {@link org.apache.kafka.common.serialization.ByteArrayDeserializer},
 * which is the binary output of this serializer — they pair correctly.
 *
 * <p>Intentionally minimal: Protobuf's own {@link MessageLite#toByteArray()} handles
 * all encoding. No schema registry or external dependency required.
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
