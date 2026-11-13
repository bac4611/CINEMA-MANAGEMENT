package com.cinema.management.service.recommendation;

import com.cinema.management.model.dto.request.GenreRecommendationInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultGenreRecommendationServiceTests {
    private final DefaultGenreRecommendationService service = new DefaultGenreRecommendationService();

    @Test
    void exactTrainingChoiceReturnsOriginalGenre() {
        var result = service.predict(new GenreRecommendationInput("vui", "tre_em", "nhe_nhang", "P"));

        assertNotNull(result);
        assertEquals("HoatHinh", result.genreCode());
        assertEquals("Hoạt hình", result.genreName());
        assertEquals(1f, result.confidence());
    }

    @Test
    void unknownCombinationUsesNearestTrainingChoice() {
        var result = service.predict(new GenreRecommendationInput("vui", "mot_minh", "vua_phai", "T15"));

        assertNotNull(result);
        assertEquals("KhoaHocVienTuong", result.genreCode());
        assertTrue(result.confidence() > 0 && result.confidence() < 1);
    }
}
