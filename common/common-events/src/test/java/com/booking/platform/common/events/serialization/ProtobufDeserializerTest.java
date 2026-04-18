package com.booking.platform.common.events.serialization;

import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtobufDeserializerTest {

    private ProtobufDeserializer<Empty> deserializer;

    @Mock
    @SuppressWarnings("unchecked")
    private Parser<Empty> mockParser;

    @BeforeEach
    void setUp() {
        deserializer = new ProtobufDeserializer<>();
    }

    @Test
    void configure_throwsWhenParserConfigKeyNotSet() {
        assertThatThrownBy(() -> deserializer.configure(Map.of(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProtobufDeserializer.PARSER_CONFIG_KEY);
    }

    @Test
    void configure_throwsWhenParserIsNull() {
        assertThatThrownBy(() -> deserializer.configure(
                Map.of(ProtobufDeserializer.PARSER_CONFIG_KEY, "not-a-parser"), false))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void deserialize_returnsNullForNullBytes() {
        deserializer.configure(Map.of(ProtobufDeserializer.PARSER_CONFIG_KEY, Empty.parser()), false);

        assertThat(deserializer.deserialize("topic", null)).isNull();
    }

    @Test
    void deserialize_parsesValidProtobufBytes() {
        deserializer.configure(Map.of(ProtobufDeserializer.PARSER_CONFIG_KEY, Empty.parser()), false);
        byte[] emptyBytes = Empty.getDefaultInstance().toByteArray();

        Empty result = deserializer.deserialize("topic", emptyBytes);

        assertThat(result).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void deserialize_throwsSerializationExceptionForInvalidBytes() throws Exception {
        when(mockParser.parseFrom(any(byte[].class)))
                .thenThrow(new InvalidProtocolBufferException("malformed"));
        deserializer.configure(Map.of(ProtobufDeserializer.PARSER_CONFIG_KEY, mockParser), false);

        assertThatThrownBy(() -> deserializer.deserialize("test-topic", new byte[]{1, 2, 3}))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("test-topic");
    }

    @Test
    void deserialize_wrapsParseExceptionWithTopicInMessage() throws Exception {
        when(mockParser.parseFrom(any(byte[].class)))
                .thenThrow(new InvalidProtocolBufferException("bad data"));
        deserializer.configure(Map.of(ProtobufDeserializer.PARSER_CONFIG_KEY, mockParser), false);

        assertThatThrownBy(() -> deserializer.deserialize("my-topic", new byte[]{0}))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("my-topic");
    }
}
