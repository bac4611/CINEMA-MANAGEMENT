package com.cinema.management.model.dto.request;

public record GenreRecommendationInput(
        String mood,
        String companion,
        String intensity,
        String ageRating) {
}
