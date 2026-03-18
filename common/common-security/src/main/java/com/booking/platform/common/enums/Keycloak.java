package com.booking.platform.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Keycloak {
    CUSTOMERS_GROUP("customers"),
    EMPLOYEES_GROUP("employees"),
    GRANT_TYPE_PASSWORD("password"),
    GRANT_TYPE_REFRESH_TOKEN("refresh_token");

    private final String value;

    Keycloak(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
