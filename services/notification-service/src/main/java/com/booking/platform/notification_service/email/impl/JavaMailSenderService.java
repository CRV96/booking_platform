package com.booking.platform.notification_service.email.impl;

import com.booking.platform.notification_service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

/**
 * Central email sending service for notification-service.
 *
 * <p>Combines {@link JavaMailSender} (Spring Mail, backed by MailHog in dev) with
 * {@link TemplateEngine} (Thymeleaf) to render HTML templates and send them as
 * MIME emails.
 *
 * <p>All outgoing emails use the configured {@code notification.mail.from} address.
 * In dev, MailHog intercepts every outgoing message — nothing is delivered to real
 * inboxes. View sent emails at {@code http://localhost:8025}.
 *
 * <h2>Exception strategy</h2>
 * <ul>
 *   <li>{@link MessagingException} — thrown when the MIME message cannot be constructed
 *       (bad address format, encoding issue). This is a <b>code bug</b>, not a transient
 *       infrastructure failure — retrying will not help, so it is caught, logged, and
 *       swallowed.</li>
 *   <li>{@link MailException} — thrown when the SMTP server is unreachable or rejects
 *       the message (e.g. MailHog down, network error). This is a <b>transient</b>
 *       infrastructure failure — it is re-thrown so the {@code DefaultErrorHandler} in
 *       {@code KafkaConsumerConfig} can retry the Kafka message up to 3 times with
 *       exponential backoff, then forward it to the Dead Letter Topic if all retries
 *       fail.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JavaMailSenderService implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.mail.from}")
    private String fromAddress;

    /**
     * Renders a Thymeleaf template and sends it as an HTML email.
     *
     * @param to           recipient email address
     * @param subject      email subject line
     * @param templateName Thymeleaf template name (without .html extension),
     *                     resolved from {@code classpath:/templates/}
     * @param variables    key-value pairs passed to the template context
     */
    @Override
    public void sendHtml(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            // ── 1. Render template ─────────────────────────────────────────
            Map<String, Object> enrichedVars = new HashMap<>(variables);
            enrichedVars.putIfAbsent("year", Year.now().getValue());

            Context context = new Context();
            context.setVariables(enrichedVars);
            String htmlBody = templateEngine.process(templateName, context);

            // ── 2. Build MIME message ──────────────────────────────────────
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = isHtml

            // ── 3. Send ────────────────────────────────────────────────────
            mailSender.send(message);
            log.info("[EMAIL_SENT] to='{}', subject='{}', template='{}'", to, subject, templateName);

        } catch (MessagingException e) {
            // Code bug (bad address, encoding) — not retryable, log and swallow.
            log.error("[EMAIL_FAILED] Failed to build MIME message for to='{}', template='{}': {}",
                    to, templateName, e.getMessage(), e);
        }
        // MailException (SMTP down, connection refused) is intentionally NOT caught here.
        // It propagates to the @KafkaListener method, which lets DefaultErrorHandler
        // retry the message with exponential backoff, then route it to the DLT.
    }
}
