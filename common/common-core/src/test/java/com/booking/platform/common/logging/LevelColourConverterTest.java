package com.booking.platform.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LevelColourConverterTest {

    private LevelColourConverter converter;

    @Mock
    private ILoggingEvent event;

    @BeforeEach
    void setUp() {
        converter = new LevelColourConverter();
    }

    @Test
    void error_returnsBoldRed() {
        when(event.getLevel()).thenReturn(Level.ERROR);
        assertThat(converter.getForegroundColorCode(event)).isEqualTo("1;31");
    }

    @Test
    void warn_returnsYellow() {
        when(event.getLevel()).thenReturn(Level.WARN);
        assertThat(converter.getForegroundColorCode(event)).isEqualTo("33");
    }

    @Test
    void info_returnsGreen() {
        when(event.getLevel()).thenReturn(Level.INFO);
        assertThat(converter.getForegroundColorCode(event)).isEqualTo("32");
    }

    @Test
    void debug_returnsWhite() {
        when(event.getLevel()).thenReturn(Level.DEBUG);
        assertThat(converter.getForegroundColorCode(event)).isEqualTo("97");
    }

    @Test
    void trace_returnsBlue() {
        when(event.getLevel()).thenReturn(Level.TRACE);
        assertThat(converter.getForegroundColorCode(event)).isEqualTo("34");
    }
}
