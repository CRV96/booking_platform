package com.booking.platform.graphql_gateway.exception;

import com.booking.platform.graphql_gateway.constants.GatewayConstants;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@Slf4j
public class GraphQLExceptionHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof GraphQLException gqlEx) {
            return buildError(env, gqlEx.getMessage(), gqlEx.getCode(), mapErrorType(gqlEx.getErrorCode()));
        }

        if (ex instanceof AuthenticationException) {
            return buildError(env, ErrorCode.UNAUTHENTICATED.getDefaultMessage(),
                             ErrorCode.UNAUTHENTICATED.getCode(), ErrorType.UNAUTHORIZED);
        }

        if (ex instanceof AccessDeniedException) {
            return buildError(env, ErrorCode.FORBIDDEN.getDefaultMessage(),
                             ErrorCode.FORBIDDEN.getCode(), ErrorType.FORBIDDEN);
        }

        if (ex instanceof StatusRuntimeException grpcEx) {
            ErrorCode errorCode = mapGrpcStatus(grpcEx.getStatus().getCode());
            String message = grpcEx.getStatus().getDescription() != null 
                ? grpcEx.getStatus().getDescription() 
                : errorCode.getDefaultMessage();
            return buildError(env, message, errorCode.getCode(), mapErrorType(errorCode));
        }

        // Log unexpected errors
        ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.GRPC_CALL_FAILED, ex);
        return buildError(env, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), 
                         ErrorCode.INTERNAL_ERROR.getCode(), ErrorType.INTERNAL_ERROR);
    }

    private GraphQLError buildError(DataFetchingEnvironment env, String message, String code, ErrorType errorType) {
        return GraphqlErrorBuilder.newError(env)
                .message(message)
                .errorType(errorType)
                .extensions(Map.of(
                        GatewayConstants.GraphQL.EXTENSION_CODE, code,
                        GatewayConstants.GraphQL.EXTENSION_TIMESTAMP, Instant.now().toString(),
                        GatewayConstants.GraphQL.EXTENSION_PATH, env.getExecutionStepInfo().getPath().toString()
                ))
                .build();
    }

    private ErrorType mapErrorType(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_CREDENTIALS, INVALID_TOKEN, TOKEN_EXPIRED, UNAUTHENTICATED -> ErrorType.UNAUTHORIZED;
            case FORBIDDEN, USER_DISABLED -> ErrorType.FORBIDDEN;
            case USER_NOT_FOUND, NOT_FOUND -> ErrorType.NOT_FOUND;
            case VALIDATION_ERROR, INVALID_INPUT, USER_ALREADY_EXISTS -> ErrorType.BAD_REQUEST;
            case RATE_LIMITED -> ErrorType.FORBIDDEN;
            case INTERNAL_ERROR, SERVICE_UNAVAILABLE, NOT_IMPLEMENTED -> ErrorType.INTERNAL_ERROR;
        };
    }

    private ErrorCode mapGrpcStatus(Status.Code code) {
        return switch (code) {
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case ALREADY_EXISTS -> ErrorCode.USER_ALREADY_EXISTS;
            case UNAUTHENTICATED -> ErrorCode.INVALID_CREDENTIALS;
            case PERMISSION_DENIED -> ErrorCode.FORBIDDEN;
            case INVALID_ARGUMENT -> ErrorCode.INVALID_INPUT;
            case UNAVAILABLE -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
