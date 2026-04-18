package com.booking.platform.user_service.validation;

import com.booking.platform.common.grpc.user.LoginRequest;
import com.booking.platform.common.grpc.user.RegisterRequest;
import com.booking.platform.user_service.exception.ValidationException;
import com.booking.platform.user_service.properties.ValidationProperties;
import com.booking.platform.user_service.validation.impl.AuthValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthValidatorImplTest {

    private AuthValidatorImpl validator;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        validator = new AuthValidatorImpl(new ValidationProperties(null, 0, 0, 0, 0, 0, 0), ms);
    }

    // ── validateRegisterRequest ───────────────────────────────────────────────

    @Test
    void validateRegisterRequest_validRequest_doesNotThrow() {
        assertThatCode(() -> validator.validateRegisterRequest(validRegister()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateRegisterRequest_blankEmail_throwsWithRequiredMessage() {
        assertThatThrownBy(() -> validator.validateRegisterRequest(
                RegisterRequest.newBuilder().setEmail("").setPassword("Pass1234!").setFirstName("John").setLastName("Doe").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Email is required");
    }

    @Test
    void validateRegisterRequest_invalidEmailFormat_throwsWithFormatMessage() {
        assertThatThrownBy(() -> validator.validateRegisterRequest(
                RegisterRequest.newBuilder().setEmail("not-an-email").setPassword("Pass1234!").setFirstName("John").setLastName("Doe").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid email format");
    }

    @Test
    void validateRegisterRequest_emailTooLong_throwsWithLengthMessage() {
        String longEmail = "a".repeat(250) + "@b.com"; // > 255
        assertThatThrownBy(() -> validator.validateRegisterRequest(
                RegisterRequest.newBuilder().setEmail(longEmail).setPassword("Pass1234!").setFirstName("John").setLastName("Doe").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("255");
    }

    @Test
    void validateRegisterRequest_blankPassword_throwsWithRequiredMessage() {
        assertThatThrownBy(() -> validator.validateRegisterRequest(
                RegisterRequest.newBuilder().setEmail("a@b.com").setPassword("").setFirstName("John").setLastName("Doe").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Password is required");
    }

    @Test
    void validateRegisterRequest_passwordTooShort_throwsWithLengthMessage() {
        assertThatThrownBy(() -> validator.validateRegisterRequest(
                RegisterRequest.newBuilder().setEmail("a@b.com").setPassword("short").setFirstName("John").setLastName("Doe").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("8");
    }

    @Test
    void validateRegisterRequest_passwordTooLong_throwsWithLengthMessage() {
        assertThatThrownBy(() -> validator.validateRegisterRequest(
                RegisterRequest.newBuilder().setEmail("a@b.com").setPassword("p".repeat(129)).setFirstName("John").setLastName("Doe").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("128");
    }

    @Test
    void validateRegisterRequest_blankFirstName_throwsWithRequiredMessage() {
        assertThatThrownBy(() -> validator.validateRegisterRequest(
                RegisterRequest.newBuilder().setEmail("a@b.com").setPassword("Pass1234!").setFirstName("").setLastName("Doe").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("First name is required");
    }

    @Test
    void validateRegisterRequest_firstNameTooLong_throwsWithLengthMessage() {
        assertThatThrownBy(() -> validator.validateRegisterRequest(
                RegisterRequest.newBuilder().setEmail("a@b.com").setPassword("Pass1234!").setFirstName("a".repeat(101)).setLastName("Doe").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("100");
    }

    @Test
    void validateRegisterRequest_blankLastName_throwsWithRequiredMessage() {
        assertThatThrownBy(() -> validator.validateRegisterRequest(
                RegisterRequest.newBuilder().setEmail("a@b.com").setPassword("Pass1234!").setFirstName("John").setLastName("").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Last name is required");
    }

    @Test
    void validateRegisterRequest_lastNameTooLong_throwsWithLengthMessage() {
        assertThatThrownBy(() -> validator.validateRegisterRequest(
                RegisterRequest.newBuilder().setEmail("a@b.com").setPassword("Pass1234!").setFirstName("John").setLastName("a".repeat(101)).build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("100");
    }

    @Test
    void validateRegisterRequest_multipleErrors_joinsAllInMessage() {
        assertThatThrownBy(() -> validator.validateRegisterRequest(
                RegisterRequest.newBuilder().setEmail("").setPassword("").setFirstName("").setLastName("").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContainingAll("Email", "Password", "First name", "Last name");
    }

    // ── validateLoginRequest ──────────────────────────────────────────────────

    @Test
    void validateLoginRequest_validRequest_doesNotThrow() {
        assertThatCode(() -> validator.validateLoginRequest(
                LoginRequest.newBuilder().setUsername("john").setPassword("pass").build()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateLoginRequest_blankUsername_throwsWithRequiredMessage() {
        assertThatThrownBy(() -> validator.validateLoginRequest(
                LoginRequest.newBuilder().setUsername("").setPassword("pass").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Username is required");
    }

    @Test
    void validateLoginRequest_blankPassword_throwsWithRequiredMessage() {
        assertThatThrownBy(() -> validator.validateLoginRequest(
                LoginRequest.newBuilder().setUsername("john").setPassword("").build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Password is required");
    }

    // ── validateRefreshToken ──────────────────────────────────────────────────

    @Test
    void validateRefreshToken_validToken_doesNotThrow() {
        assertThatCode(() -> validator.validateRefreshToken("some-token")).doesNotThrowAnyException();
    }

    @Test
    void validateRefreshToken_blankToken_throwsWithRequiredMessage() {
        assertThatThrownBy(() -> validator.validateRefreshToken("   "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Refresh token is required");
    }

    @Test
    void validateRefreshToken_nullToken_throwsValidationException() {
        assertThatThrownBy(() -> validator.validateRefreshToken(null))
                .isInstanceOf(ValidationException.class);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private RegisterRequest validRegister() {
        return RegisterRequest.newBuilder()
                .setEmail("user@example.com")
                .setPassword("SecurePass1!")
                .setFirstName("John")
                .setLastName("Doe")
                .build();
    }
}
