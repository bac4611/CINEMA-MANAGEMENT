package com.cinema.management.service.authentication;

import com.cinema.management.model.entity.account.User;

public interface JwtTokenService {
    JwtTokenResult generateAccessToken(User user);
}

