package com.cinema.management.service.authentication;

import java.time.Instant;

public record JwtTokenResult(
        String accessToken,
        String tokenType,
        Instant expiresAt) {
}

