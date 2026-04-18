package com.booking.platform.user_service.validation;

import com.booking.platform.common.grpc.user.UpdateUserRequest;
import com.booking.platform.user_service.exception.ValidationException;
import com.booking.platform.user_service.properties.ValidationProperties;
import com.booking.platform.user_service.validation.impl.UserValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserValidatorImplTest {

    private UserValidatorImpl validator;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        validator = new UserValidatorImpl(new ValidationProperties(null, 0, 0, 0, 0, 0, 0), ms);
    }

    // ── validateUpdateUserRequest ─────────────────────────────────────────────

    @Test
    void validateUpdateUserRequest_onlyUserIdPresent_doesNotThrow() {
        assertThatCode(() -> validator.validateUpdateUserRequest(
                UpdateUserRequest.newBuilder().setUserId("user-123").build()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateUpdateUserRequest_blankUserId_throwsWithRequiredMessage() {
        assertThatThrownBy(() -> validator.validateUpdateUserRequest(
                UpdateUserRequest.newBuilder().setUserId("").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("User ID is required");
    }

    @Test
    void validateUpdateUserRequest_invalidEmail_throwsWithFormatMessage() {
        assertThatThrownBy(() -> validator.validateUpdateUserRequest(
                UpdateUserRequest.newBuilder().setUserId("user-123").setEmail("bad-email").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid email format");
    }

    @Test
    void validateUpdateUserRequest_validEmail_doesNotThrow() {
        assertThatCode(() -> validator.validateUpdateUserRequest(
                UpdateUserRequest.newBuilder().setUserId("user-123").setEmail("new@example.com").build()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateUpdateUserRequest_firstNameTooLong_throwsWithLengthMessage() {
        assertThatThrownBy(() -> validator.validateUpdateUserRequest(
                UpdateUserRequest.newBuilder().setUserId("user-123").setFirstName("a".repeat(101)).build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("100");
    }

    @Test
    void validateUpdateUserRequest_lastNameTooLong_throwsWithLengthMessage() {
        assertThatThrownBy(() -> validator.validateUpdateUserRequest(
                UpdateUserRequest.newBuilder().setUserId("user-123").setLastName("b".repeat(101)).build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("100");
    }

    @Test
    void validateUpdateUserRequest_absentOptionalFields_skipsTheirValidation() {
        // email absent — even though empty string isn't valid, absent field is fine
        assertThatCode(() -> validator.validateUpdateUserRequest(
                UpdateUserRequest.newBuilder().setUserId("user-123").build()))
                .doesNotThrowAnyException();
    }

    // ── validateUserId ────────────────────────────────────────────────────────

    @Test
    void validateUserId_valid_doesNotThrow() {
        assertThatCode(() -> validator.validateUserId("user-123")).doesNotThrowAnyException();
    }

    @Test
    void validateUserId_blank_throwsWithRequiredMessage() {
        assertThatThrownBy(() -> validator.validateUserId(""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("User ID is required");
    }

    @Test
    void validateUserId_null_throwsValidationException() {
        assertThatThrownBy(() -> validator.validateUserId(null))
                .isInstanceOf(ValidationException.class);
    }

    // ── validateUsername ──────────────────────────────────────────────────────

    @Test
    void validateUsername_valid_doesNotThrow() {
        assertThatCode(() -> validator.validateUsername("john.doe")).doesNotThrowAnyException();
    }

    @Test
    void validateUsername_blank_throwsWithRequiredMessage() {
        assertThatThrownBy(() -> validator.validateUsername(""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Username is required");
    }

    // ── validateEmail ─────────────────────────────────────────────────────────

    @Test
    void validateEmail_valid_doesNotThrow() {
        assertThatCode(() -> validator.validateEmail("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    void validateEmail_blank_throwsWithRequiredMessage() {
        assertThatThrownBy(() -> validator.validateEmail(""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Email is required");
    }

    @Test
    void validateEmail_invalidFormat_throwsWithFormatMessage() {
        assertThatThrownBy(() -> validator.validateEmail("not-an-email"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid email format");
    }
}
