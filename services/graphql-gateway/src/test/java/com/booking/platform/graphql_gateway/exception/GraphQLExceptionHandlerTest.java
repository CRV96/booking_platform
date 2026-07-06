package com.booking.platform.graphql_gateway.exception;

import graphql.GraphQLError;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphQLExceptionHandlerTest {

    @Mock private DataFetchingEnvironment env;
    @Mock private ExecutionStepInfo stepInfo;
    @Mock private ResultPath resultPath;
    @Mock private Field field;

    @InjectMocks private GraphQLExceptionHandler handler;

    @BeforeEach
    void setUp() {
        when(env.getField()).thenReturn(field);
        when(field.getSourceLocation()).thenReturn(null);
        when(env.getExecutionStepInfo()).thenReturn(stepInfo);
        when(stepInfo.getPath()).thenReturn(resultPath);
        when(resultPath.toString()).thenReturn("/query/test");
    }

    // ── GraphQLException mapping ──────────────────────────────────────────────

    @Test
    void resolveToSingleError_graphQLException_usesMessageAndCode() {
        GraphQLException ex = new GraphQLException(ErrorCode.USER_NOT_FOUND, "Custom not found");

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getMessage()).isEqualTo("Custom not found");
        assertThat(error.getExtensions().get("code")).isEqualTo("USER_001");
    }

    @Test
    void resolveToSingleError_graphQLException_unauthenticated_returnsUnauthorized() {
        GraphQLException ex = new GraphQLException(ErrorCode.UNAUTHENTICATED);

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
    }

    @Test
    void resolveToSingleError_graphQLException_forbidden_returnsForbidden() {
        GraphQLException ex = new GraphQLException(ErrorCode.FORBIDDEN);

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
    }

    @Test
    void resolveToSingleError_graphQLException_notFound_returnsNotFound() {
        GraphQLException ex = new GraphQLException(ErrorCode.NOT_FOUND);

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @Test
    void resolveToSingleError_graphQLException_validationError_returnsBadRequest() {
        GraphQLException ex = new GraphQLException(ErrorCode.VALIDATION_ERROR);

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    void resolveToSingleError_graphQLException_internalError_returnsInternalError() {
        GraphQLException ex = new GraphQLException(ErrorCode.INTERNAL_ERROR);

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
    }

    // ── AuthenticationException ───────────────────────────────────────────────

    @Test
    void resolveToSingleError_authenticationException_returnsUnauthorized() {
        AuthenticationException ex = new AuthenticationException("bad creds") {};

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        assertThat(error.getExtensions().get("code")).isEqualTo(ErrorCode.UNAUTHENTICATED.getCode());
    }

    // ── AccessDeniedException ─────────────────────────────────────────────────

    @Test
    void resolveToSingleError_accessDeniedException_returnsForbidden() {
        AccessDeniedException ex = new AccessDeniedException("denied");

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        assertThat(error.getExtensions().get("code")).isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    // ── StatusRuntimeException (gRPC) ─────────────────────────────────────────

    @Test
    void resolveToSingleError_grpcNotFound_returnsNotFound() {
        StatusRuntimeException ex = Status.NOT_FOUND.withDescription("Event not found").asRuntimeException();

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        assertThat(error.getMessage()).isEqualTo("Event not found");
    }

    @Test
    void resolveToSingleError_grpcAlreadyExists_returnsBadRequest() {
        StatusRuntimeException ex = Status.ALREADY_EXISTS.asRuntimeException();

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(error.getExtensions().get("code")).isEqualTo(ErrorCode.USER_ALREADY_EXISTS.getCode());
    }

    @Test
    void resolveToSingleError_grpcUnauthenticated_returnsUnauthorized() {
        StatusRuntimeException ex = Status.UNAUTHENTICATED.asRuntimeException();

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
    }

    @Test
    void resolveToSingleError_grpcPermissionDenied_returnsForbidden() {
        StatusRuntimeException ex = Status.PERMISSION_DENIED.asRuntimeException();

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
    }

    @Test
    void resolveToSingleError_grpcInvalidArgument_returnsBadRequest() {
        StatusRuntimeException ex = Status.INVALID_ARGUMENT.withDescription("Bad input").asRuntimeException();

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    void resolveToSingleError_grpcUnavailable_returnsInternalError() {
        StatusRuntimeException ex = Status.UNAVAILABLE.asRuntimeException();

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        assertThat(error.getExtensions().get("code")).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    void resolveToSingleError_grpcUnknown_returnsInternalError() {
        StatusRuntimeException ex = Status.UNKNOWN.asRuntimeException();

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
    }

    @Test
    void resolveToSingleError_grpcNoDescription_usesDefaultMessage() {
        StatusRuntimeException ex = Status.NOT_FOUND.asRuntimeException(); // no description

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getMessage()).isEqualTo(ErrorCode.NOT_FOUND.getDefaultMessage());
    }

    // ── Unknown exception ─────────────────────────────────────────────────────

    @Test
    void resolveToSingleError_unknownException_returnsInternalError() {
        RuntimeException ex = new RuntimeException("something went wrong");

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        assertThat(error.getExtensions().get("code")).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
    }

    // ── Extensions ────────────────────────────────────────────────────────────

    @Test
    void resolveToSingleError_always_includesCodeTimestampAndPath() {
        GraphQLException ex = new GraphQLException(ErrorCode.USER_NOT_FOUND);

        GraphQLError error = handler.resolveToSingleError(ex, env);

        assertThat(error.getExtensions()).containsKey("code");
        assertThat(error.getExtensions()).containsKey("timestamp");
        assertThat(error.getExtensions()).containsKey("path");
        assertThat(error.getExtensions().get("path")).isEqualTo("/query/test");
    }
}
