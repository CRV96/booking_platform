package com.booking.platform.graphql_gateway.security;

import com.booking.platform.graphql_gateway.exception.ErrorCode;
import com.booking.platform.graphql_gateway.exception.GraphQLException;
import com.booking.platform.graphql_gateway.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Aspect that enforces authentication on all GraphQL resolver methods.
 *
 * By default, all methods annotated with {@code @QueryMapping} or {@code @MutationMapping}
 * require authentication. Methods can opt-out by adding the {@code @PublicEndpoint} annotation.
 *
 * This provides "secure by default" behavior — forgetting to add security
 * results in a protected endpoint, not an exposed one.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(1)
@ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "true", matchIfMissing = true)
public class AuthenticationAspect {

    private final AuthService authService;

    /**
     * Intercepts all GraphQL query and mutation resolver methods.
     * Requires authentication unless the method is annotated with {@code @PublicEndpoint}.
     */
    @Around("@annotation(org.springframework.graphql.data.method.annotation.QueryMapping) || " +
            "@annotation(org.springframework.graphql.data.method.annotation.MutationMapping)")
    public Object enforceAuthentication(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Check if method is marked as public
        if (method.isAnnotationPresent(PublicEndpoint.class)) {
            log.debug("Public endpoint accessed: {}.{}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName());
            return joinPoint.proceed();
        }

        // Require authentication for non-public endpoints
        if (!authService.isAuthenticated()) {
            log.warn("Authentication required for: {}.{}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName());
            throw new GraphQLException(ErrorCode.UNAUTHENTICATED);
        }

        log.debug("Authenticated access to: {}.{}",
                method.getDeclaringClass().getSimpleName(),
                method.getName());
        return joinPoint.proceed();
    }
}
