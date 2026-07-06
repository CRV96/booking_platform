package com.booking.platform.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RolesTest {

    @Test
    void customer_hasCorrectValue() {
        assertThat(Roles.CUSTOMER.getValue()).isEqualTo("customer");
    }

    @Test
    void employee_hasCorrectValue() {
        assertThat(Roles.EMPLOYEE.getValue()).isEqualTo("employee");
    }

    @Test
    void admin_hasCorrectValue() {
        assertThat(Roles.ADMIN.getValue()).isEqualTo("admin");
    }

    @Test
    void values_hasThreeEntries() {
        assertThat(Roles.values()).hasSize(3);
    }

    @Test
    void valueOf_returnsCorrectConstant() {
        assertThat(Roles.valueOf("CUSTOMER")).isEqualTo(Roles.CUSTOMER);
        assertThat(Roles.valueOf("EMPLOYEE")).isEqualTo(Roles.EMPLOYEE);
        assertThat(Roles.valueOf("ADMIN")).isEqualTo(Roles.ADMIN);
    }

    @Test
    void getValue_hasJsonValueAnnotation() throws NoSuchMethodException {
        Method method = Roles.class.getMethod("getValue");
        assertThat(method.isAnnotationPresent(JsonValue.class)).isTrue();
    }
}
