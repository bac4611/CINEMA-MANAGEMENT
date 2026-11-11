package com.cinema.management.service.authentication;

import com.cinema.management.model.entity.account.User;

import java.util.concurrent.CompletableFuture;

public interface PasswordResetService {
    String generateAndApplyToken(User user);
    boolean canSendPasswordResetEmail(User user);
    boolean isTokenValid(User user, String token);
    void clearToken(User user);
    CompletableFuture<Void> sendPasswordResetEmail(User user, String resetLink);
    CompletableFuture<Void> sendGoogleOnlyPasswordHelpEmail(User user);
}

