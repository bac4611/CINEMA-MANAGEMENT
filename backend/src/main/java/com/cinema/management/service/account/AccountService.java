package com.cinema.management.service.account;

import com.cinema.management.model.dto.request.LoginRequest;
import com.cinema.management.model.dto.request.ProfileRequest;
import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.viewmodel.account.AuthViewModel;
import com.cinema.management.model.viewmodel.account.ResetPasswordViewModel;
import com.cinema.management.model.viewmodel.account.AccountProfile;
import com.cinema.management.service.authentication.JwtTokenResult;

import java.util.Optional;
import java.util.function.BiFunction;

public interface AccountService {
    boolean isDevelopment();
    boolean isGoogleLoginEnabled();
    boolean isDevelopmentPasswordlessLogin(String email);
    LoginResult authenticate(LoginRequest request, boolean allowDevelopmentPasswordless);
    JwtTokenResult generateAccessToken(User user);
    RegistrationResult register(AuthViewModel model, BiFunction<Integer, String, String> verificationLink);
    void forgotPassword(String email, BiFunction<Integer, String, String> resetLink);
    boolean isPasswordResetTokenValid(Integer userId, String token);
    boolean resetPassword(ResetPasswordViewModel model);
    boolean confirmEmail(Integer userId, String token);
    VerificationResult resendVerificationEmail(String email, BiFunction<Integer, String, String> verificationLink);
    Optional<AccountProfile> loadProfile(int userId);
    boolean updateProfile(int userId, ProfileRequest model);

    enum LoginStatus { SUCCESS, INVALID_CREDENTIALS, INACTIVE, EMAIL_UNCONFIRMED }
    record LoginResult(LoginStatus status, User user) { }

    enum RegistrationStatus { CREATED, DUPLICATE_ACCOUNT, FAILED, VERIFICATION_EMAIL_FAILED }
    record RegistrationResult(RegistrationStatus status, String email) { }

    enum VerificationStatus { SENT, MISSING_EMAIL, USER_NOT_FOUND, ALREADY_CONFIRMED, INACTIVE, COOLDOWN, FAILED }
    record VerificationResult(VerificationStatus status, String email) { }
}
