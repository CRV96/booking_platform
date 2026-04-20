package com.booking.platform.graphql_gateway.filter;

import com.booking.platform.graphql_gateway.constants.GatewayConstants;
import com.booking.platform.graphql_gateway.dto.RateLimitResult;
import com.booking.platform.graphql_gateway.properties.RateLimitProperties;
import com.booking.platform.graphql_gateway.service.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitFilterTest {

    @Mock private RateLimitService rateLimitService;
    @Mock private RateLimitProperties properties;

    @InjectMocks private RateLimitFilter filter;

    private final RateLimitProperties.Tier anonTier = new RateLimitProperties.Tier(30, 60);
    private final RateLimitProperties.Tier authTier = new RateLimitProperties.Tier(100, 60);

    @BeforeEach
    void setUp() {
        when(properties.getAnonymous()).thenReturn(anonTier);
        when(properties.getAuthenticated()).thenReturn(authTier);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private RateLimitResult allowed(long count, int limit) {
        return new RateLimitResult(true, count, limit, 0);
    }

    private RateLimitResult blocked(long count, int limit) {
        return new RateLimitResult(false, count, limit, 30);
    }

    private void setJwtAuthentication(String userId) {
        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .claim("sub", userId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    // ── Non-GraphQL path — passthrough ────────────────────────────────────────

    @Test
    void doFilter_nonGraphqlPath_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verifyNoInteractions(rateLimitService);
        assertThat(chain.getRequest()).isNotNull();
    }

    // ── Anonymous user ────────────────────────────────────────────────────────

    @Test
    void doFilter_anonymousUser_usesIpIdentity() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(GatewayConstants.GraphQL.PATH);
        request.setRemoteAddr("192.168.1.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimitService.checkLimit(anyString(), anyInt(), anyInt()))
                .thenReturn(allowed(1, 30));

        filter.doFilter(request, response, new MockFilterChain());

        verify(rateLimitService).checkLimit(startsWith(GatewayConstants.RateLimit.IDENTITY_IP), eq(30), eq(60));
    }

    @Test
    void doFilter_anonymousUser_extractsIpFromXForwardedFor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(GatewayConstants.GraphQL.PATH);
        request.addHeader(GatewayConstants.Http.HEADER_FORWARDED_FOR, "10.0.0.5, 172.16.0.1");
        when(rateLimitService.checkLimit(anyString(), anyInt(), anyInt()))
                .thenReturn(allowed(1, 30));

        filter.doFilter(request, response(), new MockFilterChain());

        verify(rateLimitService).checkLimit(
                eq(GatewayConstants.RateLimit.IDENTITY_IP + "10.0.0.5"), anyInt(), anyInt());
    }

    @Test
    void doFilter_anonymousUser_usesRemoteAddrWhenNoForwardedHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(GatewayConstants.GraphQL.PATH);
        request.setRemoteAddr("10.1.2.3");
        when(rateLimitService.checkLimit(anyString(), anyInt(), anyInt()))
                .thenReturn(allowed(1, 30));

        filter.doFilter(request, response(), new MockFilterChain());

        verify(rateLimitService).checkLimit(
                eq(GatewayConstants.RateLimit.IDENTITY_IP + "10.1.2.3"), anyInt(), anyInt());
    }

    // ── Authenticated user ────────────────────────────────────────────────────

    @Test
    void doFilter_authenticatedUser_usesUserIdentity() throws Exception {
        setJwtAuthentication("user-42");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(GatewayConstants.GraphQL.PATH);
        when(rateLimitService.checkLimit(anyString(), anyInt(), anyInt()))
                .thenReturn(allowed(1, 100));

        filter.doFilter(request, response(), new MockFilterChain());

        verify(rateLimitService).checkLimit(
                eq(GatewayConstants.RateLimit.IDENTITY_USER + "user-42"), eq(100), eq(60));
    }

    @Test
    void doFilter_authenticatedUser_usesAuthenticatedTierLimit() throws Exception {
        setJwtAuthentication("user-1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(GatewayConstants.GraphQL.PATH);
        when(rateLimitService.checkLimit(anyString(), anyInt(), anyInt()))
                .thenReturn(allowed(5, 100));

        filter.doFilter(request, response(), new MockFilterChain());

        verify(rateLimitService).checkLimit(anyString(), eq(100), anyInt());
    }

    // ── Allowed — headers added ───────────────────────────────────────────────

    @Test
    void doFilter_allowed_forwardsRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(GatewayConstants.GraphQL.PATH);
        MockFilterChain chain = new MockFilterChain();
        when(rateLimitService.checkLimit(anyString(), anyInt(), anyInt()))
                .thenReturn(allowed(5, 30));

        filter.doFilter(request, response(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doFilter_allowed_addsRateLimitHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(GatewayConstants.GraphQL.PATH);
        MockHttpServletResponse response = response();
        when(rateLimitService.checkLimit(anyString(), anyInt(), anyInt()))
                .thenReturn(allowed(5, 30));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(GatewayConstants.Http.HEADER_RATE_LIMIT)).isEqualTo("30");
        assertThat(response.getHeader(GatewayConstants.Http.HEADER_RATE_LIMIT_REMAINING)).isEqualTo("25"); // 30-5
    }

    // ── Blocked — 429 response ────────────────────────────────────────────────

    @Test
    void doFilter_blocked_returns429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(GatewayConstants.GraphQL.PATH);
        MockHttpServletResponse response = response();
        when(rateLimitService.checkLimit(anyString(), anyInt(), anyInt()))
                .thenReturn(blocked(35, 30));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void doFilter_blocked_doesNotForwardRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(GatewayConstants.GraphQL.PATH);
        MockFilterChain chain = new MockFilterChain();
        when(rateLimitService.checkLimit(anyString(), anyInt(), anyInt()))
                .thenReturn(blocked(35, 30));

        filter.doFilter(request, response(), chain);

        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void doFilter_blocked_addsRetryAfterHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(GatewayConstants.GraphQL.PATH);
        MockHttpServletResponse response = response();
        when(rateLimitService.checkLimit(anyString(), anyInt(), anyInt()))
                .thenReturn(blocked(35, 30));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(GatewayConstants.Http.HEADER_RETRY_AFTER)).isEqualTo("30");
    }

    @Test
    void doFilter_blocked_responseBodyContainsRateCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(GatewayConstants.GraphQL.PATH);
        MockHttpServletResponse response = response();
        when(rateLimitService.checkLimit(anyString(), anyInt(), anyInt()))
                .thenReturn(blocked(35, 30));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getContentAsString()).contains("RATE_001");
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }
}
