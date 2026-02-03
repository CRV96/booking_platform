package com.booking.platform.graphql_gateway.exception;

import lombok.Getter;

@Getter
public class GraphQLException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public GraphQLException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public GraphQLException(ErrorCode errorCode, String detail) {
        super(detail != null ? detail : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public GraphQLException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail != null ? detail : errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
