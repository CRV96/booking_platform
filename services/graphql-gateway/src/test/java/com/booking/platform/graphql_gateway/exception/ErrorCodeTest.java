package com.booking.platform.graphql_gateway.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void allCodes_haveUniqueCodeStrings() {
        Set<String> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .collect(Collectors.toSet());

        assertThat(codes).hasSize(ErrorCode.values().length);
    }

    @Test
    void allCodes_haveNonBlankDefaultMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getDefaultMessage())
                    .as("default message for %s", code)
                    .isNotBlank();
        }
    }

    @Test
    void allCodes_haveNonBlankCode() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getCode())
                    .as("code string for %s", code)
                    .isNotBlank();
        }
    }

    @Test
    void specificCodes_matchExpectedValues() {
        assertThat(ErrorCode.INVALID_CREDENTIALS.getCode()).isEqualTo("AUTH_001");
        assertThat(ErrorCode.USER_ALREADY_EXISTS.getCode()).isEqualTo("AUTH_002");
        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo("AUTHZ_001");
        assertThat(ErrorCode.UNAUTHENTICATED.getCode()).isEqualTo("AUTHZ_002");
        assertThat(ErrorCode.RATE_LIMITED.getCode()).isEqualTo("RATE_001");
        assertThat(ErrorCode.INTERNAL_ERROR.getCode()).isEqualTo("GEN_001");
    }
}
