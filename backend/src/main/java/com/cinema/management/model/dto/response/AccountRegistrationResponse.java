package com.cinema.management.model.dto.response;

public record AccountRegistrationResponse(
        String status,
        String email,
        String message) {
}
