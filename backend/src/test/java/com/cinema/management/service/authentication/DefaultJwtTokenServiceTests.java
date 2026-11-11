package com.cinema.management.service.authentication;

import com.cinema.management.model.entity.account.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class DefaultJwtTokenServiceTests {
    @Test
    void generatesBearerTokenWithConfiguredLifetime() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("security.jwt.key", "12345678901234567890123456789012")
                .withProperty("security.jwt.issuer", "cosmos-cinema")
                .withProperty("security.jwt.audience", "cosmos-api")
                .withProperty("security.jwt.expires-minutes", "15");
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        DefaultJwtTokenService service = new DefaultJwtTokenService(
                environment, Clock.fixed(now, ZoneOffset.UTC));
        User user = new User();
        user.setUserId(42);
        user.setFullName("Nguyen Van A");
        user.setEmail("a@example.com");
        user.setRole("USER");

        JwtTokenResult result = service.generateAccessToken(user);

        assertEquals("Bearer", result.tokenType());
        assertFalse(result.accessToken().isBlank());
        assertEquals(now.plusSeconds(15 * 60), result.expiresAt());
    }
}
