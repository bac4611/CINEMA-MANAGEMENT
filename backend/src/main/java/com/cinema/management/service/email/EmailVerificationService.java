package com.cinema.management.service.email;

import com.cinema.management.model.entity.account.User;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

@Service
public class EmailVerificationService {
    public static final Duration TOKEN_LIFETIME = Duration.ofHours(24);
    public static final Duration RESEND_COOLDOWN = Duration.ofMinutes(2);
    private final EmailSender emailSender;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public String generateAndApplyToken(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        user.setEmailVerificationTokenHash(hash(token));
        user.setEmailVerificationTokenExpiresAt(now.plus(TOKEN_LIFETIME));
        user.setEmailVerificationLastSentAt(now);
        return token;
    }

    public boolean canSendVerificationEmail(User user) {
        return user.getEmailVerificationLastSentAt() == null
                || !Instant.now().isBefore(user.getEmailVerificationLastSentAt().plus(RESEND_COOLDOWN));
    }

    public boolean isTokenValid(User user, String token) {
        if (token == null || token.isBlank() || user.getEmailVerificationTokenHash() == null
                || user.getEmailVerificationTokenExpiresAt() == null
                || user.getEmailVerificationTokenExpiresAt().isBefore(Instant.now())) {
            return false;
        }
        try {
            return MessageDigest.isEqual(HexFormat.of().parseHex(user.getEmailVerificationTokenHash()),
                    HexFormat.of().parseHex(hash(token)));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public void markEmailConfirmed(User user) {
        user.setEmailConfirmed(true);
        user.setEmailVerificationTokenHash(null);
        user.setEmailVerificationTokenExpiresAt(null);
    }

    public CompletableFuture<Void> sendVerificationEmail(User user, String link) {
        String body = """
                <p>Xin chào %s,</p>
                <p>Cảm ơn bạn đã đăng ký COSMOS Cinema.</p>
                <p><a href="%s">Xác minh email</a></p>
                <p>Liên kết có hiệu lực trong 24 giờ.</p>
                <p>Nếu bạn không tạo tài khoản này, vui lòng bỏ qua email.</p>
                """.formatted(HtmlUtils.htmlEscape(user.getFullName()), HtmlUtils.htmlEscape(link));
        return emailSender.sendHtmlEmail(user.getEmail(), "Xác minh email COSMOS Cinema", body);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
