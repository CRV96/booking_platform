package com.booking.platform.common.events.serialization;

import com.google.protobuf.MessageLite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtobufSerializerTest {

    private ProtobufSerializer serializer;

    @Mock
    private MessageLite message;

    @BeforeEach
    void setUp() {
        serializer = new ProtobufSerializer();
    }

    @Test
    void serialize_returnsNullForNullMessage() {
        assertThat(serializer.serialize("topic", null)).isNull();
    }

    @Test
    void serialize_returnsByteArrayFromMessage() {
        byte[] expected = {1, 2, 3, 4};
        when(message.toByteArray()).thenReturn(expected);

        byte[] result = serializer.serialize("any-topic", message);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void serialize_returnsEmptyArrayForEmptyMessage() {
        when(message.toByteArray()).thenReturn(new byte[0]);

        byte[] result = serializer.serialize("topic", message);

        assertThat(result).isEmpty();
    }

    @Test
    void serialize_topicIsIgnored() {
        byte[] expected = {9, 8, 7};
        when(message.toByteArray()).thenReturn(expected);

        assertThat(serializer.serialize("topic-a", message))
                .isEqualTo(serializer.serialize("topic-b", message));
    }
}
