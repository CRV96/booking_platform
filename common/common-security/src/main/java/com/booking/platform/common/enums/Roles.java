package com.booking.platform.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing user roles in the booking platform.
 */
public enum Roles {
    CUSTOMER("customer"),
    EMPLOYEE("employee"),
    ADMIN("admin");

    private final String value;

    Roles(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}