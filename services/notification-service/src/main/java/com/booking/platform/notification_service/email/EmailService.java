package com.booking.platform.notification_service.email;

import java.util.Map;

public interface EmailService {
    void sendHtml(String to, String subject, String templateName, Map<String, Object> variables);
}
