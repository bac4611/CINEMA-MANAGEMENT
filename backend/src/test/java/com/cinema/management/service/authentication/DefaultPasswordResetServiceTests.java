package com.cinema.management.service.authentication;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.service.email.EmailSender;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPasswordResetServiceTests {
    private static final EmailSender UNUSED_EMAIL_SENDER =
            (recipient, subject, body) -> CompletableFuture.completedFuture(null);

    @Test
    void generatedTokenIsValidUntilCleared() {
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        DefaultPasswordResetService service = new DefaultPasswordResetService(
                UNUSED_EMAIL_SENDER,
                new SecureRandom(),
                Clock.fixed(now, ZoneOffset.UTC));
        User user = new User();

        String token = service.generateAndApplyToken(user);

        assertTrue(service.isTokenValid(user, token));
        assertFalse(service.isTokenValid(user, token + "invalid"));
        assertFalse(service.canSendPasswordResetEmail(user));
        service.clearToken(user);
        assertFalse(service.isTokenValid(user, token));
    }
}
