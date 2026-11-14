package com.cinema.management.service.account;

import com.cinema.management.model.dto.request.LoginRequest;
import com.cinema.management.model.dto.request.ProfileRequest;
import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.viewmodel.account.AccountProfile;
import com.cinema.management.model.viewmodel.account.AuthViewModel;
import com.cinema.management.model.viewmodel.account.ResetPasswordViewModel;
import com.cinema.management.repository.account.UserRepository;
import com.cinema.management.repository.booking.BookingRepository;
import com.cinema.management.service.authentication.JwtTokenResult;
import com.cinema.management.service.authentication.JwtTokenService;
import com.cinema.management.service.authentication.PasswordResetService;
import com.cinema.management.service.email.EmailVerificationService;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

@Service
public class DefaultAccountService implements AccountService {
    private static final Logger log = LoggerFactory.getLogger(DefaultAccountService.class);
    private static final Set<String> DEVELOPMENT_EMAILS = Set.of("khach@gmail.com", "staff@gmail.com", "admin@gmail.com");
    private final UserRepository users;
    private final BookingRepository bookings;
    private final EmailVerificationService verification;
    private final PasswordResetService passwordReset;
    private final JwtTokenService jwtTokens;
    private final Environment environment;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public DefaultAccountService(UserRepository users, BookingRepository bookings,
                                 EmailVerificationService verification, PasswordResetService passwordReset,
                                 JwtTokenService jwtTokens, Environment environment) {
        this.users = users;
        this.bookings = bookings;
        this.verification = verification;
        this.passwordReset = passwordReset;
        this.jwtTokens = jwtTokens;
        this.environment = environment;
    }

    @Override
    public boolean isDevelopment() {
        return environment.acceptsProfiles(Profiles.of("dev", "development", "Development"));
    }

    @Override
    public boolean isGoogleLoginEnabled() {
        return hasText(environment.getProperty("spring.security.oauth2.client.registration.google.client-id"))
                && hasText(environment.getProperty("spring.security.oauth2.client.registration.google.client-secret"));
    }

    @Override
    public boolean isDevelopmentPasswordlessLogin(String email) {
        return isDevelopment() && DEVELOPMENT_EMAILS.contains(normalizeEmail(email));
    }

    @Override
    public LoginResult authenticate(LoginRequest request, boolean allowDevelopmentPasswordless) {
        String email = normalizeEmail(request.getEmail());
        boolean passwordless = allowDevelopmentPasswordless && isDevelopmentPasswordlessLogin(email);
        User user = users.findByNormalizedEmail(email).orElse(null);
        if (user == null || (!passwordless && !isPasswordValid(request.getPassword(), user.getPasswordHash()))) {
            return new LoginResult(LoginStatus.INVALID_CREDENTIALS, null);
        }
        if (!user.isStatus()) {
            return new LoginResult(LoginStatus.INACTIVE, user);
        }
        if (!user.isEmailConfirmed()) {
            return new LoginResult(LoginStatus.EMAIL_UNCONFIRMED, user);
        }
        if (passwordless) {
            log.info("Development passwordless login succeeded. Database role: {}", user.getRole());
        }
        return new LoginResult(LoginStatus.SUCCESS, user);
    }

    @Override
    public JwtTokenResult generateAccessToken(User user) {
        return jwtTokens.generateAccessToken(user);
    }

    // Repository saves commit before sending email, so delivery failure never rolls back account creation.
    @Override
    public RegistrationResult register(AuthViewModel model, BiFunction<Integer, String, String> verificationLink) {
        String email = normalizeEmail(model.getEmail());
        if (users.findByNormalizedEmail(email).isPresent()) {
            return new RegistrationResult(RegistrationStatus.DUPLICATE_ACCOUNT, email);
        }
        User user = new User();
        user.setFullName(model.getFullName().trim());
        user.setEmail(email);
        user.setPhoneNumber(model.getPhoneNumber().trim());
        user.setDateOfBirth(model.getDateOfBirth());
        user.setPasswordHash(passwords.encode(model.getPassword()));
        user.setRole("KhachHang");
        user.setStatus(true);
        boolean development = isDevelopment();
        user.setEmailConfirmed(development);
        String token = development ? null : verification.generateAndApplyToken(user);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Recheck after rollback to distinguish a concurrent duplicate email from other constraints.
            if (users.findByNormalizedEmail(email).isPresent()) {
                return new RegistrationResult(RegistrationStatus.DUPLICATE_ACCOUNT, email);
            }
            log.error("Failed to create account", exception);
            return new RegistrationResult(RegistrationStatus.FAILED, email);
        } catch (RuntimeException exception) {
            log.error("Failed to create account", exception);
            return new RegistrationResult(RegistrationStatus.FAILED, email);
        }
        if (development) {
            log.warn("Development authentication is active: registered account {} was confirmed without email.", email);
            return new RegistrationResult(RegistrationStatus.CREATED, email);
        }
        try {
            verification.sendVerificationEmail(user, verificationLink.apply(user.getUserId(), token)).join();
            return new RegistrationResult(RegistrationStatus.CREATED, email);
        } catch (RuntimeException exception) {
            log.error("Failed to send verification email for user {}", user.getUserId(), exception);
            return new RegistrationResult(RegistrationStatus.VERIFICATION_EMAIL_FAILED, email);
        }
    }

    @Override
    public void forgotPassword(String email, BiFunction<Integer, String, String> resetLink) {
        User user = users.findByNormalizedEmail(normalizeEmail(email)).orElse(null);
        if (user == null || !passwordReset.canSendPasswordResetEmail(user)) {
            return;
        }
        try {
            if ("Google".equalsIgnoreCase(user.getExternalProvider()) && !hasText(user.getPasswordHash())) {
                user.setPasswordResetLastSentAt(Instant.now());
                users.saveAndFlush(user);
                passwordReset.sendGoogleOnlyPasswordHelpEmail(user).join();
            } else if (hasText(user.getPasswordHash())) {
                String token = passwordReset.generateAndApplyToken(user);
                users.saveAndFlush(user);
                passwordReset.sendPasswordResetEmail(user, resetLink.apply(user.getUserId(), token)).join();
            }
        } catch (RuntimeException exception) {
            log.warn("Password reset email could not be sent", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPasswordResetTokenValid(Integer userId, String token) {
        return userId != null && users.findById(userId)
                .map(user -> passwordReset.isTokenValid(user, token)).orElse(false);
    }

    @Override
    @Transactional
    public boolean resetPassword(ResetPasswordViewModel model) {
        if (model.getUserId() == null) {
            return false;
        }
        User user = users.findForUpdate(model.getUserId()).orElse(null);
        if (user == null || !passwordReset.isTokenValid(user, model.getToken())) {
            return false;
        }
        user.setPasswordHash(passwords.encode(model.getNewPassword()));
        passwordReset.clearToken(user);
        users.saveAndFlush(user);
        return true;
    }

    @Override
    @Transactional
    public boolean confirmEmail(Integer userId, String token) {
        if (userId == null) {
            return false;
        }
        User user = users.findForUpdate(userId).orElse(null);
        if (user == null || !verification.isTokenValid(user, token)) {
            return false;
        }
        verification.markEmailConfirmed(user);
        users.saveAndFlush(user);
        return true;
    }

    @Override
    public VerificationResult resendVerificationEmail(String email, BiFunction<Integer, String, String> verificationLink) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank()) {
            return new VerificationResult(VerificationStatus.MISSING_EMAIL, normalizedEmail);
        }
        User user = users.findByNormalizedEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return new VerificationResult(VerificationStatus.USER_NOT_FOUND, normalizedEmail);
        }
        if (user.isEmailConfirmed()) {
            return new VerificationResult(VerificationStatus.ALREADY_CONFIRMED, user.getEmail());
        }
        if (!user.isStatus()) {
            return new VerificationResult(VerificationStatus.INACTIVE, user.getEmail());
        }
        if (!verification.canSendVerificationEmail(user)) {
            return new VerificationResult(VerificationStatus.COOLDOWN, user.getEmail());
        }
        String token = verification.generateAndApplyToken(user);
        try {
            users.saveAndFlush(user);
            verification.sendVerificationEmail(user, verificationLink.apply(user.getUserId(), token)).join();
            return new VerificationResult(VerificationStatus.SENT, user.getEmail());
        } catch (RuntimeException exception) {
            log.error("Failed to resend verification email for user {}", user.getUserId(), exception);
            return new VerificationResult(VerificationStatus.FAILED, user.getEmail());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountProfile> loadProfile(int userId) {
        return users.findById(userId).map(user -> {
            var confirmedBookings = bookings.findByUserUserIdAndStatusOrderByBookingDateDesc(userId, "Confirmed");
            // Initialize each collection separately to avoid joining multiple Hibernate bags.
            confirmedBookings.forEach(booking -> {
                booking.getTickets().forEach(ticket -> {
                    Hibernate.initialize(ticket.getSeat());
                    if (ticket.getShowtime() != null) {
                        Hibernate.initialize(ticket.getShowtime());
                        Hibernate.initialize(ticket.getShowtime().getMovie());
                        Hibernate.initialize(ticket.getShowtime().getRoom());
                    }
                });
                booking.getPayments().forEach(payment -> Hibernate.initialize(payment.getPaymentMethod()));
            });
            return new AccountProfile(user.getUserId(), user.getFullName(), user.getEmail(),
                    user.getPhoneNumber(), user.getDateOfBirth(), confirmedBookings);
        });
    }

    @Override
    @Transactional
    public boolean updateProfile(int userId, ProfileRequest model) {
        User user = users.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        user.setFullName(model.getFullName().trim());
        user.setEmail(normalizeEmail(model.getEmail()));
        user.setPhoneNumber(model.getPhoneNumber().trim());
        user.setDateOfBirth(model.getDateOfBirth());
        users.saveAndFlush(user);
        return true;
    }

    private boolean isPasswordValid(String password, String hash) {
        if (password == null || !hasText(hash)) {
            return false;
        }
        try {
            return passwords.matches(password, hash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
