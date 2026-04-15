package com.booking.platform.common.security;

import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for validating JWT tokens against Keycloak's JWKS endpoint.
 *
 * Validates:
 * - Signature (via JWKS public keys)
 * - Expiry (exp claim)
 * - Issuer (iss claim matches configured issuer)
 * - Blacklist (token not revoked)
 *
 * This provides defense-in-depth: even though the gateway validates tokens,
 * each downstream service independently verifies them before trusting claims.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "true")
public class JwtValidatorService {

    private final JwtDecoder jwtDecoder;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtValidatorService(
            @Value("${security.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${security.jwt.issuer-uri}") String issuerUri,
            TokenBlacklistService tokenBlacklistService) {

        this.tokenBlacklistService = tokenBlacklistService;

        ApplicationLogger.logMessage(log, Level.INFO, "Initializing JWT validator with JWKS URI: {}", jwkSetUri);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        // Configure validators for expiry and issuer
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(issuerUri);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                issuerValidator
        );

        decoder.setJwtValidator(validator);
        this.jwtDecoder = decoder;

        ApplicationLogger.logMessage(log, Level.INFO, "JWT validator initialized successfully");
    }

    /**
     * Validates and decodes a JWT token.
     *
     * Validation includes:
     * 1. Signature verification (via JWKS)
     * 2. Expiry check (exp claim)
     * 3. Issuer check (iss claim)
     * 4. Blacklist check (revoked tokens)
     *
     * @param token the raw JWT token string (without "Bearer " prefix)
     * @return the validated and decoded JWT
     * @throws JwtException if validation fails
     */
    public Jwt validateAndDecode(String token) throws JwtException {
        // 1-3: Signature, expiry, issuer validation
        Jwt jwt = jwtDecoder.decode(token);

        // 4: Blacklist check
        String jti = jwt.getId();
        if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
            ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.AUTH_TOKEN_INVALID, "Token has been revoked: {}", jti);
            throw new JwtException("Token has been revoked");
        }

        return jwt;
    }
}
