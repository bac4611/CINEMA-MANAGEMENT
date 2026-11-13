package com.cinema.management.model.dto.response;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.service.authentication.JwtTokenResult;
import java.time.Instant;

public record AccountLoginResponse(String accessToken, String tokenType, Instant expiresAt, UserInfo user) {
    public static AccountLoginResponse of(JwtTokenResult token, User user) {
        return new AccountLoginResponse(token.accessToken(), token.tokenType(), token.expiresAt(),
                new UserInfo(user.getUserId(), user.getFullName(), user.getEmail(), user.getRole()));
    }

    public record UserInfo(Integer userId, String fullName, String email, String role) { }
}
