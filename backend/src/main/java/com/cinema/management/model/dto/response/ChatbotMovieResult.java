package com.cinema.management.model.dto.response;

public record ChatbotMovieResult(
        Integer movieId,
        String title,
        String genres,
        String ageRating,
        short duration,
        String synopsis,
        String nextShowtime,
        int showtimeCount,
        double score) {
}
