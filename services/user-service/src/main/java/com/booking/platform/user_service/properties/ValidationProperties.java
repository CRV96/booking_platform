package com.booking.platform.user_service.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for validation rules.
 */
@ConfigurationProperties(prefix = "validation")
public record ValidationProperties(
    String emailPattern,
    int minPasswordLength,
    int maxPasswordLength,
    int maxNameLength,
    int maxEmailLength
) {
    /**
     * Default values if not specified in configuration.
     */
    public ValidationProperties {
        if (emailPattern == null || emailPattern.isBlank()) {
            emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        }
        if (minPasswordLength <= 0) {
            minPasswordLength = 8;
        }
        if (maxPasswordLength <= 0) {
            maxPasswordLength = 128;
        }
        if (maxNameLength <= 0) {
            maxNameLength = 100;
        }
        if (maxEmailLength <= 0) {
            maxEmailLength = 255;
        }
    }
}
