package com.cinema.management.service.authentication;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.service.email.EmailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

@Service
public class DefaultPasswordResetService implements PasswordResetService {
    public static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final EmailSender emailSender;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public DefaultPasswordResetService(EmailSender emailSender) {
        this(emailSender, new SecureRandom(), Clock.systemUTC());
    }

    DefaultPasswordResetService(EmailSender emailSender, SecureRandom secureRandom, Clock clock) {
        this.emailSender = emailSender;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Override
    public String generateAndApplyToken(User user) {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant now = clock.instant();

        user.setPasswordResetTokenHash(hashToken(token));
        user.setPasswordResetTokenExpiresAt(now.plus(TOKEN_LIFETIME));
        user.setPasswordResetLastSentAt(now);
        return token;
    }

    @Override
    public boolean canSendPasswordResetEmail(User user) {
        Instant lastSent = user.getPasswordResetLastSentAt();
        return lastSent == null || !clock.instant().isBefore(lastSent.plus(RESEND_COOLDOWN));
    }

    @Override
    public boolean isTokenValid(User user, String token) {
        if (token == null || token.isBlank()
                || user.getPasswordResetTokenHash() == null
                || user.getPasswordResetTokenExpiresAt() == null
                || user.getPasswordResetTokenExpiresAt().isBefore(clock.instant())) {
            return false;
        }

        try {
            byte[] expectedHash = HexFormat.of().parseHex(user.getPasswordResetTokenHash());
            byte[] actualHash = HexFormat.of().parseHex(hashToken(token));
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public void clearToken(User user) {
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetTokenExpiresAt(null);
    }

    @Override
    public CompletableFuture<Void> sendPasswordResetEmail(User user, String resetLink) {
        String fullName = HtmlUtils.htmlEscape(user.getFullName());
        String safeLink = HtmlUtils.htmlEscape(resetLink);
        String body = """
                <p>Xin chào %s,</p>
                <p>Chúng tôi đã nhận được yêu cầu đặt lại tài khoản COSMOS của bạn.</p>
                <p><a href="%s">Đặt lại mật khẩu tại đây</a></p>
                <p>Liên kết này có hiệu lực trong 30 phút và chỉ dùng được 1 lần.</p>
                <p>Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>
                """.formatted(fullName, safeLink);

        return emailSender.sendHtmlEmail(
                user.getEmail(), "Đặt lại mật khẩu COSMOS Cinema", body);
    }

    @Override
    public CompletableFuture<Void> sendGoogleOnlyPasswordHelpEmail(User user) {
        String fullName = HtmlUtils.htmlEscape(user.getFullName());
        String providerName = "google".equalsIgnoreCase(user.getExternalProvider())
                ? "Google" : "tài khoản liên kết bên ngoài";
        String body = """
                <p>Xin chào %s,</p>
                <p>Tài khoản COSMOS Cinema của bạn đang đăng nhập bằng %s.</p>
                <p>Vui lòng dùng nút Đăng nhập với Google trên trang đăng nhập COSMOS Cinema.
                Nếu cần khôi phục mật khẩu Google, hãy thực hiện trong tài khoản Google của bạn.</p>
                <p>Nếu bạn không yêu cầu hỗ trợ đăng nhập, vui lòng bỏ qua email này.</p>
                """.formatted(fullName, providerName);

        return emailSender.sendHtmlEmail(
                user.getEmail(), "Hướng dẫn đăng nhập COSMOS Cinema", body);
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().withUpperCase()
                    .formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

