package com.cinema.management.service.recommendation;

import com.cinema.management.model.dto.request.GenreRecommendationInput;
import com.cinema.management.model.dto.response.GenreRecommendationResult;

public interface GenreRecommendationService {
    GenreRecommendationResult predict(GenreRecommendationInput input);
}
