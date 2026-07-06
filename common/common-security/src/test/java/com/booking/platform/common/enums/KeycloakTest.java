package com.booking.platform.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakTest {

    @Test
    void customersGroup_hasCorrectValue() {
        assertThat(Keycloak.CUSTOMERS_GROUP.getValue()).isEqualTo("customers");
    }

    @Test
    void employeesGroup_hasCorrectValue() {
        assertThat(Keycloak.EMPLOYEES_GROUP.getValue()).isEqualTo("employees");
    }

    @Test
    void grantTypePassword_hasCorrectValue() {
        assertThat(Keycloak.GRANT_TYPE_PASSWORD.getValue()).isEqualTo("password");
    }

    @Test
    void grantTypeRefreshToken_hasCorrectValue() {
        assertThat(Keycloak.GRANT_TYPE_REFRESH_TOKEN.getValue()).isEqualTo("refresh_token");
    }

    @Test
    void values_hasFourEntries() {
        assertThat(Keycloak.values()).hasSize(4);
    }

    @Test
    void valueOf_returnsCorrectConstant() {
        assertThat(Keycloak.valueOf("CUSTOMERS_GROUP")).isEqualTo(Keycloak.CUSTOMERS_GROUP);
        assertThat(Keycloak.valueOf("EMPLOYEES_GROUP")).isEqualTo(Keycloak.EMPLOYEES_GROUP);
    }

    @Test
    void getValue_hasJsonValueAnnotation() throws NoSuchMethodException {
        Method method = Keycloak.class.getMethod("getValue");
        assertThat(method.isAnnotationPresent(JsonValue.class)).isTrue();
    }
}
