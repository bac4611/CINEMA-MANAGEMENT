package com.cinema.management.model.dto.response;

import java.util.List;

public record ChatbotResponse(
        String intent,
        String mood,
        String recommendedGenreCode,
        String recommendedGenreName,
        String reply,
        List<ChatbotMovieResult> movies) {
}
