package com.booking.platform.notification_service.messaging.deserializer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

/**
 * Kafka value deserializer for Protobuf messages.
 *
 * <p>The matching counterpart to {@code ProtobufSerializer} in event-service.
 * Converts raw bytes from Kafka back into a Protobuf {@link MessageLite} object.
 *
 * <p>Because each Kafka topic carries a single message type, the concrete
 * {@link Parser} is provided by each {@link org.springframework.kafka.core.ConsumerFactory}
 * bean via the {@link #configure(Map, boolean)} method using the config key
 * {@value #PARSER_CONFIG_KEY}.
 *
 * <p>Usage in {@code KafkaConsumerConfig}:
 * <pre>
 * config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, EventCreatedEvent.parser());
 * </pre>
 */
public class ProtobufDeserializer<T extends MessageLite> implements Deserializer<T> {

    /**
     * Config key used to supply the Protobuf {@link Parser} to this deserializer.
     * Must be set in the consumer factory configuration map before use.
     */
    public static final String PARSER_CONFIG_KEY = "protobuf.parser";

    @SuppressWarnings("unchecked")
    private Parser<T> parser;

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Map<String, ?> configs, boolean isKey) {
        parser = (Parser<T>) configs.get(PARSER_CONFIG_KEY);
        if (parser == null) {
            throw new IllegalStateException(
                    "ProtobufDeserializer requires '" + PARSER_CONFIG_KEY + "' to be set in consumer config");
        }
    }

    /**
     * Deserializes raw Protobuf bytes from Kafka into the target message type.
     *
     * @param topic   Kafka topic name (unused — type is determined by the configured parser)
     * @param bytes   raw bytes from Kafka; {@code null} returns {@code null}
     * @return the deserialized Protobuf message, or {@code null} if bytes is null
     * @throws SerializationException if bytes cannot be parsed
     */
    @Override
    public T deserialize(String topic, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return parser.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException(
                    "Failed to deserialize Protobuf message from topic '" + topic + "'", e);
        }
    }
}
