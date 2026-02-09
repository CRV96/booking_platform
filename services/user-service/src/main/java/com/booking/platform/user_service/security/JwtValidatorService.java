package com.booking.platform.user_service.security;

import lombok.extern.slf4j.Slf4j;
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
 *
 * This provides defense-in-depth: even though the gateway validates tokens,
 * the user-service independently verifies them before trusting claims.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "true")
public class JwtValidatorService {

    private final JwtDecoder jwtDecoder;

    public JwtValidatorService(
            @Value("${security.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${security.jwt.issuer-uri}") String issuerUri) {

        log.debug("Initializing JWT validator with JWKS URI: {}", jwkSetUri);

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

        log.debug("JWT validator initialized successfully");
    }

    /**
     * Validates and decodes a JWT token.
     *
     * @param token the raw JWT token string (without "Bearer " prefix)
     * @return the validated and decoded JWT
     * @throws JwtException if validation fails (invalid signature, expired, wrong issuer)
     */
    public Jwt validateAndDecode(String token) throws JwtException {
        return jwtDecoder.decode(token);
    }
}
