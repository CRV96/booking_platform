package com.booking.platform.common.logging;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LogErrorCodeTest {

    @Test
    void allCodes_haveUniqueNumericValues() {
        int[] codes = Arrays.stream(LogErrorCode.values())
                .mapToInt(LogErrorCode::getCode)
                .toArray();

        Set<Integer> unique = Arrays.stream(LogErrorCode.values())
                .map(LogErrorCode::getCode)
                .collect(Collectors.toSet());

        assertThat(unique).hasSize(codes.length);
    }

    @Test
    void getFormattedCode_wrapsCodeInBrackets() {
        assertThat(LogErrorCode.VERIFICATION_EMAIL_FAILED.getFormattedCode()).isEqualTo("[1001]");
        assertThat(LogErrorCode.PAYMENT_GATEWAY_UNAVAILABLE.getFormattedCode()).isEqualTo("[2001]");
        assertThat(LogErrorCode.AUTH_TOKEN_INVALID.getFormattedCode()).isEqualTo("[8003]");
    }

    @Test
    void getCode_returnsNumericValue() {
        assertThat(LogErrorCode.VERIFICATION_EMAIL_FAILED.getCode()).isEqualTo(1001);
        assertThat(LogErrorCode.USER_CREATION_FAILED.getCode()).isEqualTo(1002);
        assertThat(LogErrorCode.PAYMENT_GATEWAY_UNAVAILABLE.getCode()).isEqualTo(2001);
        assertThat(LogErrorCode.EMAIL_SEND_FAILED.getCode()).isEqualTo(5001);
        assertThat(LogErrorCode.TLS_CONFIG_FAILED.getCode()).isEqualTo(8004);
    }

    @Test
    void getDescription_returnsDescription() {
        assertThat(LogErrorCode.VERIFICATION_EMAIL_FAILED.getDescription())
                .isEqualTo("Failed to send verification email");
        assertThat(LogErrorCode.PAYMENT_PROCESSING_FAILED.getDescription())
                .isEqualTo("Payment processing failed");
        assertThat(LogErrorCode.AUTH_TOKEN_INVALID.getDescription())
                .isEqualTo("Authentication token is invalid or expired");
    }

    @Test
    void allCodes_fallWithinExpectedServiceRanges() {
        for (LogErrorCode code : LogErrorCode.values()) {
            int c = code.getCode();
            assertThat(c)
                    .as("Code %s (%d) must fall within 1000–8999", code.name(), c)
                    .isBetween(1000, 8999);
        }
    }

    @Test
    void allCodes_haveNonBlankDescriptions() {
        for (LogErrorCode code : LogErrorCode.values()) {
            assertThat(code.getDescription())
                    .as("Description for %s must not be blank", code.name())
                    .isNotBlank();
        }
    }
}
