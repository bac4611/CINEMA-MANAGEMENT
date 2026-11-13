package com.cinema.management.service.email;

import com.cinema.management.model.entity.account.User;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class EmailVerificationServiceTests {
    private final EmailVerificationService service = new EmailVerificationService(
            (recipient, subject, body) -> CompletableFuture.completedFuture(null));

    @Test
    void tokenIsHashedExpiresAndIsConsumedByConfirmation() {
        User user = new User();
        String token = service.generateAndApplyToken(user);
        assertNotEquals(token, user.getEmailVerificationTokenHash());
        assertTrue(service.isTokenValid(user, token));
        assertFalse(service.isTokenValid(user, token + "changed"));
        assertFalse(service.canSendVerificationEmail(user));
        user.setEmailVerificationTokenExpiresAt(Instant.now().minusSeconds(1));
        assertFalse(service.isTokenValid(user, token));
        token = service.generateAndApplyToken(user);
        service.markEmailConfirmed(user);
        assertTrue(user.isEmailConfirmed());
        assertFalse(service.isTokenValid(user, token));
    }

    @Test
    void malformedStoredHashDoesNotThrow() {
        User user = new User();
        service.generateAndApplyToken(user);
        user.setEmailVerificationTokenHash("not-hex");
        assertFalse(service.isTokenValid(user, "token"));
    }
}
