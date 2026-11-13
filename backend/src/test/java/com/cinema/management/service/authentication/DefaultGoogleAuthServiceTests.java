package com.cinema.management.service.authentication;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.repository.account.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultGoogleAuthServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final Environment environment = mock(Environment.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transaction = mock(TransactionStatus.class);
    private DefaultGoogleAuthService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);
        service = new DefaultGoogleAuthService(users, environment, transactionManager);
    }

    @Test
    void createsCustomerForVerifiedGoogleProfile() {
        when(users.findByExternalProviderIgnoreCaseAndExternalProviderKey("Google", "google-1"))
                .thenReturn(Optional.empty());
        when(users.findByNormalizedEmail("person@example.com")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0); user.setUserId(7); return user;
        });

        var result = service.authenticate(profile(true));

        assertEquals(GoogleAuthService.Status.SUCCESS, result.status());
        assertEquals("person@example.com", result.user().getEmail());
        assertEquals("KhachHang", result.user().getRole());
        assertEquals("Google", result.user().getExternalProvider());
        assertTrue(result.user().isEmailConfirmed());
        assertNull(result.user().getPasswordHash());
        verify(transactionManager).commit(transaction);
    }

    @Test
    void linksExistingEmailWithoutChangingRole() {
        User user = new User(); user.setUserId(4); user.setEmail("person@example.com");
        user.setFullName("Admin"); user.setRole("Admin"); user.setStatus(true);
        when(users.findByExternalProviderIgnoreCaseAndExternalProviderKey("Google", "google-1"))
                .thenReturn(Optional.empty());
        when(users.findByNormalizedEmail("person@example.com")).thenReturn(Optional.of(user));
        when(users.saveAndFlush(user)).thenReturn(user);

        var result = service.authenticate(profile(true));

        assertEquals(GoogleAuthService.Status.SUCCESS, result.status());
        assertEquals("Admin", result.user().getRole());
        assertEquals("google-1", result.user().getExternalProviderKey());
    }

    @Test
    void rejectsUnverifiedEmailBeforeOpeningTransaction() {
        var result = service.authenticate(profile(false));

        assertEquals(GoogleAuthService.Status.EMAIL_UNVERIFIED, result.status());
        verifyNoInteractions(users, transactionManager);
    }

    @Test
    void inactiveLinkedAccountCannotSignIn() {
        User user = new User(); user.setStatus(false);
        when(users.findByExternalProviderIgnoreCaseAndExternalProviderKey("Google", "google-1"))
                .thenReturn(Optional.of(user));

        var result = service.authenticate(profile(true));

        assertEquals(GoogleAuthService.Status.INACTIVE, result.status());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void databaseFailureRollsBackAndReturnsLinkFailure() {
        when(users.findByExternalProviderIgnoreCaseAndExternalProviderKey(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(users.findByNormalizedEmail(anyString())).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        var result = service.authenticate(profile(true));

        assertEquals(GoogleAuthService.Status.LINK_FAILED, result.status());
        verify(transactionManager).rollback(transaction);
    }

    private Map<String, Object> profile(boolean verified) {
        return Map.of("sub", "google-1", "email", " PERSON@EXAMPLE.COM ",
                "name", "Google Person", "email_verified", verified);
    }
}
