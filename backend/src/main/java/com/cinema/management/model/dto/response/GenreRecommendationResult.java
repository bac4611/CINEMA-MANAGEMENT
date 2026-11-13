package com.cinema.management.model.dto.response;

public record GenreRecommendationResult(
        String genreCode,
        String genreName,
        float confidence) {
}
