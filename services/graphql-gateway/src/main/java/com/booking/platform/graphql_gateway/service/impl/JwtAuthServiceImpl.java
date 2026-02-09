package com.booking.platform.graphql_gateway.service.impl;

import com.booking.platform.graphql_gateway.exception.ErrorCode;
import com.booking.platform.graphql_gateway.exception.GraphQLException;
import com.booking.platform.graphql_gateway.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtAuthServiceImpl implements AuthService {

    @Override
    public String getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String userId = jwtAuth.getToken().getSubject();
            if (userId != null) {
                return userId;
            }

            log.error("JWT token has no 'sub' claim. Claims present: {}",
                    jwtAuth.getToken().getClaims().keySet());
            throw new GraphQLException(ErrorCode.UNAUTHENTICATED, "Token missing user identity");
        }

        throw new GraphQLException(ErrorCode.UNAUTHENTICATED);
    }

    @Override
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwtAuth
                && jwtAuth.getToken().getSubject() != null;
    }

    @Override
    public void requireAuthentication() {
        if (!isAuthenticated()) {
            throw new GraphQLException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
