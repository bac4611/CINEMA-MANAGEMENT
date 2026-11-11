package com.cinema.management.service.authentication;

import com.cinema.management.model.entity.account.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
public class DefaultJwtTokenService implements JwtTokenService {
    private static final int DEFAULT_EXPIRATION_MINUTES = 15;
    private static final int MINIMUM_KEY_BYTES = 32;

    private final Environment environment;
    private final Clock clock;

    @Autowired
    public DefaultJwtTokenService(Environment environment) {
        this(environment, Clock.systemUTC());
    }

    DefaultJwtTokenService(Environment environment, Clock clock) {
        this.environment = environment;
        this.clock = clock;
    }

    @Override
    public JwtTokenResult generateAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(getExpirationMinutes()));
        SecretKey key = getSigningKey(getRequiredValue("security.jwt.key"));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(getRequiredValue("security.jwt.issuer"))
                .audience(List.of(getRequiredValue("security.jwt.audience")))
                .subject(String.valueOf(user.getUserId()))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString().replace("-", ""))
                .claim("name", user.getFullName())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(key).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new JwtTokenResult(token, "Bearer", expiresAt);
    }

    public static SecretKey getSigningKey(String keyValue) {
        if (keyValue == null || keyValue.isBlank()) {
            throw new IllegalStateException(
                    "JWT signing key is not configured. Set security.jwt.key or JWT_KEY.");
        }

        byte[] keyBytes = keyValue.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("JWT signing key must be at least 32 bytes.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    private String getRequiredValue(String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " is not configured.");
        }
        return value;
    }

    private int getExpirationMinutes() {
        Integer configured = environment.getProperty("security.jwt.expires-minutes", Integer.class);
        return configured != null && configured > 0 ? configured : DEFAULT_EXPIRATION_MINUTES;
    }
}

