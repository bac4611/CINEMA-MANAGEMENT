package com.cinema.management.service.account;

import com.cinema.management.model.dto.request.LoginRequest;
import com.cinema.management.model.dto.request.ProfileRequest;
import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.viewmodel.account.AuthViewModel;
import com.cinema.management.model.viewmodel.account.ResetPasswordViewModel;
import com.cinema.management.repository.account.UserRepository;
import com.cinema.management.repository.booking.BookingRepository;
import com.cinema.management.service.authentication.DefaultPasswordResetService;
import com.cinema.management.service.authentication.JwtTokenService;
import com.cinema.management.service.email.EmailSender;
import com.cinema.management.service.email.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.cinema.management.service.account.AccountService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultAccountServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final EmailSender sender = mock(EmailSender.class);
    private final EmailVerificationService verification = new EmailVerificationService(sender);
    private final DefaultPasswordResetService passwordReset = new DefaultPasswordResetService(sender);
    private DefaultAccountService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAccountService(users, bookings, verification, passwordReset,
                mock(JwtTokenService.class), environment);
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            user.setUserId(42);
            return user;
        });
        when(sender.sendHtmlEmail(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void developmentBypassIsOnlyAvailableForAllowlistedMvcAccounts() {
        environment.setActiveProfiles("dev");
        User user = confirmedUser();
        user.setEmail("admin@gmail.com");
        when(users.findByNormalizedEmail("admin@gmail.com")).thenReturn(Optional.of(user));
        LoginRequest request = login(" ADMIN@gmail.com ", "");
        assertEquals(LoginStatus.SUCCESS, service.authenticate(request, true).status());
        assertEquals(LoginStatus.INVALID_CREDENTIALS, service.authenticate(request, false).status());
        environment.setActiveProfiles("production");
        assertEquals(LoginStatus.INVALID_CREDENTIALS, service.authenticate(request, true).status());
        environment.setActiveProfiles("dev");
        assertFalse(service.isDevelopmentPasswordlessLogin("other@gmail.com"));
    }

    @Test
    void loginChecksCredentialsStatusAndEmailConfirmation() {
        User user = confirmedUser();
        when(users.findByNormalizedEmail("a@example.com")).thenReturn(Optional.of(user));
        assertEquals(LoginStatus.INVALID_CREDENTIALS, service.authenticate(login(user.getEmail(), "wrong"), false).status());
        user.setStatus(false);
        assertEquals(LoginStatus.INACTIVE, service.authenticate(login(user.getEmail(), "Password!1"), false).status());
        user.setStatus(true);
        user.setEmailConfirmed(false);
        assertEquals(LoginStatus.EMAIL_UNCONFIRMED, service.authenticate(login(user.getEmail(), "Password!1"), false).status());
        user.setEmailConfirmed(true);
        assertEquals(LoginStatus.SUCCESS, service.authenticate(login(user.getEmail(), "Password!1"), false).status());
    }

    @Test
    void deliveryFailureKeepsCreatedAccountAndPersistedVerificationToken() {
        when(sender.sendHtmlEmail(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("SMTP unavailable")));
        AuthViewModel form = new AuthViewModel();
        form.setFullName(" Nguyen Van A ");
        form.setEmail(" A@EXAMPLE.COM ");
        form.setPhoneNumber("0901234567");
        form.setDateOfBirth(LocalDate.of(2000, 1, 1));
        form.setPassword("Password!1");
        var result = service.register(form, (id, token) -> "https://cinema.test/confirm?token=" + token);
        assertEquals(RegistrationStatus.VERIFICATION_EMAIL_FAILED, result.status());
        verify(users).saveAndFlush(argThat(user -> user.getEmail().equals("a@example.com")
                && user.getRole().equals("KhachHang") && !user.isEmailConfirmed()
                && user.getEmailVerificationTokenHash() != null
                && new BCryptPasswordEncoder().matches("Password!1", user.getPasswordHash())));
    }

    @Test
    void registrationRejectsDuplicateBeforeSaving() {
        when(users.findByNormalizedEmail("a@example.com")).thenReturn(Optional.of(confirmedUser()));
        AuthViewModel form = new AuthViewModel();
        form.setEmail(" A@EXAMPLE.COM ");
        assertEquals(RegistrationStatus.DUPLICATE_ACCOUNT, service.register(form, (id, token) -> "unused").status());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void resetTokenIsSingleUseAndInvalidTokenDoesNotChangePassword() {
        User user = confirmedUser();
        String originalHash = user.getPasswordHash();
        String token = passwordReset.generateAndApplyToken(user);
        when(users.findForUpdate(42)).thenReturn(Optional.of(user));
        ResetPasswordViewModel form = new ResetPasswordViewModel();
        form.setUserId(42);
        form.setNewPassword("NewPassword!2");
        form.setToken("invalid");
        assertFalse(service.resetPassword(form));
        assertEquals(originalHash, user.getPasswordHash());
        form.setToken(token);
        assertTrue(service.resetPassword(form));
        assertTrue(new BCryptPasswordEncoder().matches("NewPassword!2", user.getPasswordHash()));
        assertNull(user.getPasswordResetTokenHash());
        assertFalse(service.resetPassword(form));
    }

    @Test
    void confirmationClearsTokenAndResendRespectsCooldown() {
        User user = confirmedUser();
        user.setEmailConfirmed(false);
        String token = verification.generateAndApplyToken(user);
        when(users.findByNormalizedEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertEquals(VerificationStatus.COOLDOWN,
                service.resendVerificationEmail(user.getEmail(), (id, value) -> "unused").status());
        verifyNoInteractions(sender);
        when(users.findForUpdate(42)).thenReturn(Optional.of(user));
        assertTrue(service.confirmEmail(42, token));
        assertTrue(user.isEmailConfirmed());
        assertNull(user.getEmailVerificationTokenHash());
        assertFalse(service.confirmEmail(42, token));
    }

    @Test
    void googleOnlyForgotPasswordSendsHelpWithoutCreatingResetToken() {
        User user = confirmedUser();
        user.setExternalProvider("Google");
        user.setPasswordHash(null);
        when(users.findByNormalizedEmail(user.getEmail())).thenReturn(Optional.of(user));
        service.forgotPassword(user.getEmail(), (id, token) -> fail("Google account must not receive a reset link"));
        verify(sender).sendHtmlEmail(eq(user.getEmail()), anyString(), anyString());
        assertNotNull(user.getPasswordResetLastSentAt());
        assertNull(user.getPasswordResetTokenHash());
    }

    @Test
    void profileOnlyUpdatesTheAuthenticatedUserAndKeepsRoleAndPassword() {
        User user = confirmedUser();
        String hash = user.getPasswordHash();
        when(users.findById(42)).thenReturn(Optional.of(user));
        ProfileRequest form = new ProfileRequest();
        form.setFullName(" Updated Name ");
        form.setEmail(user.getEmail());
        form.setPhoneNumber("0901234567");
        assertTrue(service.updateProfile(42, form));
        assertEquals("Updated Name", user.getFullName());
        assertEquals("KhachHang", user.getRole());
        assertEquals(hash, user.getPasswordHash());
    }

    private User confirmedUser() {
        User user = new User();
        user.setUserId(42);
        user.setEmail("a@example.com");
        user.setRole("KhachHang");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("Password!1"));
        user.setEmailConfirmed(true);
        return user;
    }

    private LoginRequest login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
