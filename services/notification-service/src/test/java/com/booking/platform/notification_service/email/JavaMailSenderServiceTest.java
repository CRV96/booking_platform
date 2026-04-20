package com.booking.platform.notification_service.email;

import com.booking.platform.notification_service.email.impl.JavaMailSenderService;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Year;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JavaMailSenderServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private TemplateEngine templateEngine;

    @InjectMocks private JavaMailSenderService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@booking.com");
    }

    private MimeMessage realMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void sendHtml_success_sendsEmail() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>body</html>");

        service.sendHtml("user@test.com", "Hello", "tmpl", Map.of("key", "val"));

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendHtml_passesTemplateNameToEngine() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("booking-confirmation"), any(Context.class))).thenReturn("<html/>");

        service.sendHtml("u@x.com", "Subj", "booking-confirmation", Map.of());

        verify(templateEngine).process(eq("booking-confirmation"), any(Context.class));
    }

    // ── Year enrichment ───────────────────────────────────────────────────────

    @Test
    void sendHtml_enrichesContextWithCurrentYear() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(anyString(), contextCaptor.capture())).thenReturn("<html/>");

        service.sendHtml("u@x.com", "S", "tmpl", Map.of("k", "v"));

        Context captured = contextCaptor.getValue();
        assertThat(captured.getVariable("year")).isEqualTo(Year.now().getValue());
    }

    @Test
    void sendHtml_preservesCallerVariablesInContext() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(anyString(), contextCaptor.capture())).thenReturn("<html/>");

        service.sendHtml("u@x.com", "S", "tmpl", Map.of("bookingId", "bk-1", "eventId", "ev-2"));

        Context captured = contextCaptor.getValue();
        assertThat(captured.getVariable("bookingId")).isEqualTo("bk-1");
        assertThat(captured.getVariable("eventId")).isEqualTo("ev-2");
    }

    @Test
    void sendHtml_yearNotOverriddenWhenCallerProvidesIt() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(anyString(), contextCaptor.capture())).thenReturn("<html/>");

        // caller passes their own "year" — putIfAbsent must NOT override it
        service.sendHtml("u@x.com", "S", "tmpl", Map.of("year", 2000));

        Context captured = contextCaptor.getValue();
        assertThat(captured.getVariable("year")).isEqualTo(2000);
    }

    // ── MessagingException swallowed ─────────────────────────────────────────

    @Test
    void sendHtml_messagingException_isSwallowed() throws Exception {
        MimeMessage mockMessage = mock(MimeMessage.class);
        // MimeMessageHelper constructor calls setContent when multipart=true
        doThrow(new MessagingException("bad mime")).when(mockMessage).setContent(any(MimeMultipart.class));
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html/>");

        assertThatNoException().isThrownBy(
                () -> service.sendHtml("u@x.com", "S", "tmpl", Map.of()));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ── MailException propagates ──────────────────────────────────────────────

    @Test
    void sendHtml_mailException_propagatesToCaller() {
        MimeMessage mimeMessage = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html/>");
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.sendHtml("u@x.com", "S", "tmpl", Map.of()))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("SMTP down");
    }
}
