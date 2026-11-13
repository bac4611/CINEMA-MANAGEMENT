package com.cinema.management.service.chatbot;

import com.cinema.management.model.dto.request.GenreRecommendationInput;
import com.cinema.management.model.dto.response.GenreRecommendationResult;
import com.cinema.management.model.entity.movie.Genre;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.movie.MovieGenre;
import com.cinema.management.model.entity.theater.Showtime;
import com.cinema.management.repository.movie.MovieRepository;
import com.cinema.management.service.recommendation.GenreRecommendationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultChatbotServiceTests {
    private final MovieRepository movies = mock(MovieRepository.class);
    private final GenreRecommendationService genres = mock(GenreRecommendationService.class);
    private final DefaultChatbotService service = new DefaultChatbotService(movies, genres);

    @Test
    void filtersByPredictedGenreAndReturnsScoredMovies() {
        var input = new GenreRecommendationInput("kich_tinh", "ban_be", "gay_can", "T13");
        when(genres.predict(input)).thenReturn(new GenreRecommendationResult("HanhDong", "Hành động", 1));
        Movie action = movie(1, "Action", "Hành động", "T13", "Trận chiến bảo vệ công lý", true);
        Movie horror = movie(2, "Horror", "Kinh dị", "T18", "Bí ẩn", false);
        when(movies.findAllForChatbot()).thenReturn(List.of(horror, action));

        var response = service.recommend(input);

        assertEquals("HanhDong", response.recommendedGenreCode());
        assertEquals(1, response.movies().size());
        assertEquals(1, response.movies().getFirst().movieId());
        assertEquals("Hành động", response.movies().getFirst().genres());
        assertNotNull(response.movies().getFirst().nextShowtime());
        assertTrue(response.movies().getFirst().score() > 5);
    }

    @Test
    void noMatchingMovieReturnsEmptyResultWithExplanation() {
        var input = new GenreRecommendationInput("so_hai", "mot_minh", "gay_can", "T18");
        when(genres.predict(input)).thenReturn(new GenreRecommendationResult("KinhDi", "Kinh dị", 1));
        when(movies.findAllForChatbot()).thenReturn(List.of());

        var response = service.recommend(input);

        assertTrue(response.movies().isEmpty());
        assertTrue(response.reply().contains("chưa có phim"));
    }

    private Movie movie(int id, String title, String genreName, String rating, String synopsis,
                        boolean withShowtime) {
        Movie movie = new Movie();
        movie.setMovieId(id);
        movie.setTitle(title);
        movie.setAgeRating(rating);
        movie.setSynopsis(synopsis);
        movie.setDuration((short) 120);
        Genre genre = new Genre();
        genre.setName(genreName);
        MovieGenre link = new MovieGenre();
        link.setGenre(genre);
        movie.setMovieGenres(List.of(link));
        if (withShowtime) {
            Showtime showtime = new Showtime();
            showtime.setStartTime(LocalDateTime.now().plusDays(1));
            movie.setShowtimes(List.of(showtime));
        }
        return movie;
    }
}
