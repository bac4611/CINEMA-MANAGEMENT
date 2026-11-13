package com.cinema.management.service.authentication;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.repository.account.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.Map;

@Service
public class DefaultGoogleAuthService implements GoogleAuthService {
    private static final Logger log = LoggerFactory.getLogger(DefaultGoogleAuthService.class);
    private static final String PROVIDER = "Google";

    private final UserRepository users;
    private final Environment environment;
    private final TransactionTemplate transactions;

    public DefaultGoogleAuthService(UserRepository users, Environment environment,
                                    PlatformTransactionManager transactionManager) {
        this.users = users;
        this.environment = environment;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public boolean isEnabled() {
        return hasText(environment.getProperty("spring.security.oauth2.client.registration.google.client-id"))
                && hasText(environment.getProperty("spring.security.oauth2.client.registration.google.client-secret"));
    }

    @Override
    public AuthenticationResult authenticate(Map<String, Object> attributes) {
        if (attributes == null) {
            return result(Status.INVALID_PROFILE, null, "Không đọc được thông tin đăng nhập Google.");
        }
        String providerKey = text(attributes.get("sub"));
        String email = normalizeEmail(text(attributes.get("email")));
        if (!hasText(providerKey) || !hasText(email)) {
            return result(Status.INVALID_PROFILE, null, "Tài khoản Google không cung cấp đủ email.");
        }
        if (!isVerified(attributes.get("email_verified"))) {
            return result(Status.EMAIL_UNVERIFIED, null, "Email Google chưa được xác minh.");
        }
        String fullName = text(attributes.get("name"));
        if (!hasText(fullName)) {
            fullName = email.substring(0, email.indexOf('@'));
        }
        String resolvedName = fullName.trim();
        try {
            AuthenticationResult value = transactions.execute(status -> linkAccount(
                    providerKey, email, resolvedName));
            return value == null
                    ? result(Status.LINK_FAILED, null, "Không liên kết được tài khoản Google. Vui lòng thử lại.")
                    : value;
        } catch (DataAccessException exception) {
            log.warn("Google login failed while linking provider for email {}.", email, exception);
            return result(Status.LINK_FAILED, null, "Không liên kết được tài khoản Google. Vui lòng thử lại.");
        } catch (RuntimeException exception) {
            log.error("Unexpected Google login failure for email {}.", email, exception);
            return result(Status.LINK_FAILED, null, "Đăng nhập Google thất bại. Vui lòng thử lại.");
        }
    }

    private AuthenticationResult linkAccount(String providerKey, String email, String fullName) {
        User user = users.findByExternalProviderIgnoreCaseAndExternalProviderKey(PROVIDER, providerKey)
                .orElse(null);
        if (user == null) {
            user = users.findByNormalizedEmail(email).orElse(null);
            if (user == null) {
                user = new User();
                user.setFullName(fullName);
                user.setEmail(email);
                user.setPasswordHash(null);
                user.setRole("KhachHang");
                user.setStatus(true);
            }
            if (!user.isStatus()) {
                return result(Status.INACTIVE, null, "Tài khoản của bạn đã bị khóa.");
            }
            user.setExternalProvider(PROVIDER);
            user.setExternalProviderKey(providerKey);
        } else if (!user.isStatus()) {
            return result(Status.INACTIVE, null, "Tài khoản của bạn đã bị khóa.");
        }
        user.setEmailConfirmed(true);
        user = users.saveAndFlush(user);
        return result(Status.SUCCESS, user, null);
    }

    private boolean isVerified(Object value) {
        if (value instanceof Boolean verified) return verified;
        String text = text(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String text(Object value) { return value == null ? "" : value.toString().trim(); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private AuthenticationResult result(Status status, User user, String message) {
        return new AuthenticationResult(status, user, message);
    }
}
