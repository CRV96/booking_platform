package com.booking.platform.graphql_gateway.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphQLExceptionTest {

    @Test
    void constructor_errorCodeOnly_usesDefaultMessage() {
        GraphQLException ex = new GraphQLException(ErrorCode.USER_NOT_FOUND);

        assertThat(ex.getMessage()).isEqualTo(ErrorCode.USER_NOT_FOUND.getDefaultMessage());
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(ex.getDetail()).isNull();
    }

    @Test
    void constructor_withDetail_usesDetail() {
        GraphQLException ex = new GraphQLException(ErrorCode.VALIDATION_ERROR, "Field 'email' is invalid");

        assertThat(ex.getMessage()).isEqualTo("Field 'email' is invalid");
        assertThat(ex.getDetail()).isEqualTo("Field 'email' is invalid");
    }

    @Test
    void constructor_withNullDetail_fallsBackToDefaultMessage() {
        GraphQLException ex = new GraphQLException(ErrorCode.INTERNAL_ERROR, null);

        assertThat(ex.getMessage()).isEqualTo(ErrorCode.INTERNAL_ERROR.getDefaultMessage());
        assertThat(ex.getDetail()).isNull();
    }

    @Test
    void constructor_withCause_preservesCause() {
        RuntimeException cause = new RuntimeException("underlying");
        GraphQLException ex = new GraphQLException(ErrorCode.SERVICE_UNAVAILABLE, "gRPC unavailable", cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).isEqualTo("gRPC unavailable");
    }

    @Test
    void getCode_returnsErrorCodeCode() {
        GraphQLException ex = new GraphQLException(ErrorCode.FORBIDDEN);

        assertThat(ex.getCode()).isEqualTo("AUTHZ_001");
    }

    @Test
    void getCode_matchesErrorCodeDirectly() {
        for (ErrorCode code : ErrorCode.values()) {
            GraphQLException ex = new GraphQLException(code);
            assertThat(ex.getCode()).isEqualTo(code.getCode());
        }
    }
}
