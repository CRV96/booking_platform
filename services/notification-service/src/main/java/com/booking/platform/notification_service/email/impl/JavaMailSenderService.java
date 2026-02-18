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
 * <p>Sending failures are logged and swallowed rather than propagated. This keeps
 * the Kafka consumer healthy — a transient SMTP error should not cause the consumer
 * to crash or retry the same message indefinitely. Retry + DLT logic is added in P2-06.
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
            Context context = new Context();
            context.setVariables(variables);
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
            log.error("[EMAIL_FAILED] Failed to build email for to='{}', template='{}': {}",
                    to, templateName, e.getMessage(), e);
        } catch (MailException e) {
            log.error("[EMAIL_FAILED] Failed to send email to='{}', template='{}': {}",
                    to, templateName, e.getMessage(), e);
        }
    }
}
