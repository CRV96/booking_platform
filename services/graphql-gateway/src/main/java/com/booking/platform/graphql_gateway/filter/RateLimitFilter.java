package com.booking.platform.graphql_gateway.filter;

import com.booking.platform.graphql_gateway.constants.GatewayConstants;
import com.booking.platform.graphql_gateway.properties.RateLimitProperties;
import com.booking.platform.graphql_gateway.config.SecurityConfig;
import com.booking.platform.graphql_gateway.dto.RateLimitResult;
import com.booking.platform.graphql_gateway.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * HTTP filter that enforces Redis-based rate limiting on the GraphQL endpoint.
 *
 * <p>Rate limiting tiers:
 * <ul>
 *   <li><b>Anonymous:</b> 30 req/min per IP address</li>
 *   <li><b>Authenticated:</b> 100 req/min per user ID</li>
 * </ul>
 *
 * <p>This filter is registered inside the Spring Security filter chain (via
 * {@link SecurityConfig}) so that the JWT has already been validated and the
 * {@code SecurityContextHolder} is populated when this filter runs. This allows
 * us to identify authenticated users by their JWT subject (user ID) rather than
 * falling back to IP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_JSON_TEMPLATE = """
            {"errors":[{"message":"Too many requests. Please try again later.","extensions":{"code":"RATE_001","retryAfter":%d}}]}""";

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!GatewayConstants.GraphQL.PATH.equals(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Determine identity and tier — JWT is available because this filter runs
        // after Spring Security's authentication filters in the security chain
        String identity;
        RateLimitProperties.Tier tier;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            identity = GatewayConstants.RateLimit.IDENTITY_USER + jwtAuth.getToken().getSubject();
            tier = properties.getAuthenticated();
        } else {
            identity = GatewayConstants.RateLimit.IDENTITY_IP + getClientIp(request);
            tier = properties.getAnonymous();
        }

        // Check general rate limit
        RateLimitResult result = rateLimitService.checkLimit(identity, tier.getLimit(), tier.getWindowSeconds());

        if (!result.allowed()) {
            ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.RATE_LIMIT_STORE_FAILED,
                    "Rate limit exceeded for {}: {}/{}", identity, result.currentCount(), result.limit());
            writeRateLimitResponse(response, result);
            return;
        }

        // Add rate limit headers to response
        addRateLimitHeaders(response, result);

        // Pass request through unchanged - no body reading needed
        filterChain.doFilter(request, response);
    }

    private void writeRateLimitResponse(HttpServletResponse response, RateLimitResult result) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(GatewayConstants.Http.HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
        response.setHeader(GatewayConstants.Http.HEADER_RATE_LIMIT, String.valueOf(result.limit()));
        response.setHeader(GatewayConstants.Http.HEADER_RATE_LIMIT_REMAINING, "0");
        response.setHeader(GatewayConstants.Http.HEADER_RATE_LIMIT_RESET, String.valueOf(
                Instant.now().getEpochSecond() + result.retryAfterSeconds()));

        response.getWriter().write(String.format(RATE_LIMIT_JSON_TEMPLATE, result.retryAfterSeconds()));
    }

    private void addRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
        response.setHeader(GatewayConstants.Http.HEADER_RATE_LIMIT, String.valueOf(result.limit()));
        response.setHeader(GatewayConstants.Http.HEADER_RATE_LIMIT_REMAINING, String.valueOf(result.remaining()));
    }

    private String getClientIp(HttpServletRequest request) {
        // In Docker deployment, nginx always overwrites X-Forwarded-For with
        // $remote_addr before forwarding — the value is therefore trustworthy.
        // In local dev (Option A, no nginx), the header is absent and we fall
        // back to remoteAddr, which is already the real client IP.
        String forwarded = request.getHeader(GatewayConstants.Http.HEADER_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
