package com.cinema.management.service.home;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.movie.Review;
import com.cinema.management.model.viewmodel.home.MovieReviewFormViewModel;
import com.cinema.management.model.viewmodel.home.SeatType;
import com.cinema.management.repository.account.UserRepository;
import com.cinema.management.repository.movie.*;
import com.cinema.management.repository.theater.ShowtimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultHomeServiceTests {
    private final MovieRepository movies = mock(MovieRepository.class);
    private final MovieGenreRepository genres = mock(MovieGenreRepository.class);
    private final MovieCastRepository casts = mock(MovieCastRepository.class);
    private final MovieDirectorRepository directors = mock(MovieDirectorRepository.class);
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ShowtimeRepository showtimes = mock(ShowtimeRepository.class);
    private DefaultHomeService service;

    @BeforeEach
    void setUp() {
        service = new DefaultHomeService(movies, genres, casts, directors, reviews, users, showtimes);
    }

    @Test
    void inactiveOrMissingUserCannotCreateReview() {
        MovieReviewFormViewModel form = reviewForm(9, "Hay", "4.5");

        var result = service.addReview(null, form);

        assertEquals(HomeService.ReviewStatus.UNAUTHORIZED, result.status());
        verifyNoInteractions(movies, reviews);
    }

    @Test
    void validReviewIsTrimmedRoundedAndPersisted() {
        User user = new User();
        user.setUserId(3);
        user.setStatus(true);
        Movie movie = new Movie();
        movie.setMovieId(9);
        when(users.findById(3)).thenReturn(Optional.of(user));
        when(users.getReferenceById(3)).thenReturn(user);
        when(movies.findById(9)).thenReturn(Optional.of(movie));
        when(reviews.saveAndFlush(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.addReview(3, reviewForm(9, "  Rất hay  ", "4.567"));

        assertEquals(HomeService.ReviewStatus.SUCCESS, result.status());
        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviews).saveAndFlush(saved.capture());
        assertEquals("Rất hay", saved.getValue().getContent());
        assertEquals(new BigDecimal("4.57"), saved.getValue().getRating());
        assertSame(movie, saved.getValue().getMovie());
        assertSame(user, saved.getValue().getUser());
    }

    @Test
    void persistenceFailureIsReturnedAsBusinessResult() {
        User user = new User();
        user.setStatus(true);
        Movie movie = new Movie();
        movie.setMovieId(9);
        when(users.findById(3)).thenReturn(Optional.of(user));
        when(users.getReferenceById(3)).thenReturn(user);
        when(movies.findById(9)).thenReturn(Optional.of(movie));
        when(reviews.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("database"));

        var result = service.addReview(3, reviewForm(9, "Hay", "4"));

        assertEquals(HomeService.ReviewStatus.PERSISTENCE_ERROR, result.status());
    }

    @Test
    void ticketPricingPreservesTwoFormatsAndSeatMultipliers() {
        var pricing = service.getTicketPricing();

        assertEquals(2, pricing.getMovieFormats().size());
        var imax = pricing.getMovieFormats().get(1);
        assertEquals("IMAX", imax.getId());
        assertEquals(SeatType.STANDARD, imax.getSeatPrices().get(0).getSeatType());
        assertEquals(new BigDecimal("135000"), imax.getSeatPrices().get(0).getNormalDay());
        assertEquals(new BigDecimal("247500"), imax.getSeatPrices().get(1).getWeekendOrHoliday());
        assertEquals(new BigDecimal("412500"), imax.getSeatPrices().get(2).getWeekendOrHoliday());
    }

    private MovieReviewFormViewModel reviewForm(int movieId, String comment, String rating) {
        MovieReviewFormViewModel form = new MovieReviewFormViewModel();
        form.setMovieId(movieId);
        form.setComment(comment);
        form.setRating(new BigDecimal(rating));
        return form;
    }
}
