package com.cinema.management.service.home;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.movie.Review;
import com.cinema.management.model.viewmodel.home.MovieReviewFormViewModel;
import com.cinema.management.model.viewmodel.home.SeatType;
import com.cinema.management.repository.account.UserRepository;
import com.cinema.management.repository.movie.*;
import com.cinema.management.repository.movie.projection.*;
import com.cinema.management.repository.theater.ShowtimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

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

    @Test
    void homeCardsBatchGenresAndRatingsWithoutLoadingDetailRelationships() {
        when(movies.findAllCardData()).thenReturn(List.of(card(9, "Movie", LocalDate.now())));
        when(genres.findNamesByMovieIds(List.of(9))).thenReturn(List.of(
                new MovieGenreName(9, "Drama"), new MovieGenreName(9, "Action")));
        when(reviews.findRatingTotalsByMovieIds(List.of(9), "Visible")).thenReturn(
                List.of(new MovieRatingTotals(9, new BigDecimal("4.01"), 2L)));

        var result = service.getHomeMovies();

        var movie = result.getFirst();
        assertEquals(9, movie.getMovieId());
        assertEquals("Movie", movie.getTitle());
        assertEquals("Drama, Action", movie.getGenre());
        assertEquals("/poster.jpg", movie.getPosterUrl());
        assertEquals("Synopsis", movie.getSynopsis());
        assertEquals(120, movie.getDuration());
        assertEquals("T16", movie.getAgeRating());
        assertEquals("https://youtu.be/test", movie.getTrailer());
        assertEquals("2D,IMAX", movie.getAvailableFormats());
        assertEquals(new BigDecimal("2.01"), movie.getAverageRating());
        assertEquals(2, movie.getReviewCount());
        assertEquals(new BigDecimal("2.01"), movie.getReviewSummary().getAverageRating());
        verify(movies).findAllCardData();
        verify(genres).findNamesByMovieIds(List.of(9));
        verify(reviews).findRatingTotalsByMovieIds(List.of(9), "Visible");
        verifyNoMoreInteractions(movies, genres, reviews);
        verifyNoInteractions(casts, directors, showtimes, users);
    }

    @Test
    void catalogPreservesDateBoundariesOrderingAndFourDistinctFeaturedMovies() {
        LocalDate today = LocalDate.now();
        var cards = List.of(card(1, "beta", today), card(2, "Alpha", today.minusDays(1)),
                card(3, "Zulu", today.plusDays(1)), card(4, "gamma", today.plusDays(1)),
                card(5, "Aardvark", today.plusDays(2)), card(6, "Undated", null));
        when(movies.findAllCardData()).thenReturn(cards);

        var catalog = service.getMovieCatalog();

        assertEquals(List.of(2, 1), catalog.nowShowing().stream().map(movie -> movie.getMovieId()).toList());
        assertEquals(List.of(4, 3, 5), catalog.comingSoon().stream().map(movie -> movie.getMovieId()).toList());
        assertEquals(4, catalog.featured().size());
        assertEquals(4, catalog.featured().stream().map(movie -> movie.getMovieId()).distinct().count());
        assertTrue(catalog.featured().stream().allMatch(movie -> movie.getMovieId() >= 1 && movie.getMovieId() <= 6));
        assertTrue(catalog.nowShowing().stream().allMatch(movie -> movie.getReviewCount() == 0
                && movie.getAverageRating().compareTo(BigDecimal.ZERO) == 0));
        verify(movies).findAllCardData();
        verify(genres).findNamesByMovieIds(List.of(1, 2, 3, 4, 5, 6));
        verify(reviews).findRatingTotalsByMovieIds(List.of(1, 2, 3, 4, 5, 6), "Visible");
        verifyNoMoreInteractions(movies, genres, reviews);
        verifyNoInteractions(casts, directors, showtimes, users);
    }

    @Test
    void catalogKeepsFeaturedFallbackWhenNoMoviesAreNowShowing() {
        when(movies.findAllCardData()).thenReturn(List.of(card(1, "Zulu", LocalDate.now().plusDays(1)),
                card(2, "Alpha", null)));

        var catalog = service.getMovieCatalog();

        assertEquals(2, catalog.featured().size());
        assertEquals(List.of(2, 1), catalog.nowShowing().stream().map(movie -> movie.getMovieId()).toList());
        assertEquals(List.of(1), catalog.comingSoon().stream().map(movie -> movie.getMovieId()).toList());
    }

    @Test
    void emptyCatalogDoesNotIssueEmptyInQueries() {
        when(movies.findAllCardData()).thenReturn(List.of());

        assertTrue(service.getHomeMovies().isEmpty());
        var catalog = service.getMovieCatalog();
        assertTrue(catalog.featured().isEmpty());
        assertTrue(catalog.nowShowing().isEmpty());
        assertTrue(catalog.comingSoon().isEmpty());
        verifyNoInteractions(genres, reviews, casts, directors, showtimes, users);
    }

    @Test
    void largeCatalogSplitsInListsAndKeepsMetadataFromTheLastBatch() {
        when(movies.findAllCardData()).thenReturn(IntStream.rangeClosed(1, 1001)
                .mapToObj(id -> card(id, "Movie " + id, LocalDate.now())).toList());
        when(genres.findNamesByMovieIds(List.of(1001))).thenReturn(List.of(new MovieGenreName(1001, "Drama")));
        when(reviews.findRatingTotalsByMovieIds(List.of(1001), "Visible")).thenReturn(
                List.of(new MovieRatingTotals(1001, new BigDecimal("5.00"), 1L)));

        var result = service.getHomeMovies();

        assertEquals(1001, result.size());
        assertEquals("Drama", result.getLast().getGenre());
        assertEquals(new BigDecimal("5.00"), result.getLast().getAverageRating());
        verify(genres).findNamesByMovieIds(argThat(ids -> ids.size() == 1000));
        verify(genres).findNamesByMovieIds(List.of(1001));
        verify(reviews).findRatingTotalsByMovieIds(argThat(ids -> ids.size() == 1000), eq("Visible"));
        verify(reviews).findRatingTotalsByMovieIds(List.of(1001), "Visible");
        verifyNoMoreInteractions(genres, reviews);
    }

    @Test
    void movieDetailsFetchesMetadataOnceAndUsesTheSevenDaySchedule() {
        Movie movie = new Movie();
        movie.setMovieId(9);
        when(movies.findDetailsById(9)).thenReturn(Optional.of(movie));
        when(directors.findPersonNamesByMovieId(9)).thenReturn(List.of("Director"));
        when(casts.findPersonNamesByMovieId(9)).thenReturn(List.of("Actor"));

        var result = service.getMovieDetails(9).orElseThrow();

        assertEquals("Director", result.getMovieDirector());
        assertEquals("Actor", result.getMovieCast());
        verify(movies).findDetailsById(9);
        LocalDate today = LocalDate.now();
        verify(showtimes).findMovieSchedule(9, today, today.plusDays(7));
        verify(reviews).findByMovieMovieIdAndParentReviewIsNullAndStatusAndRatingIsNotNullOrderByCreatedAtDesc(9, "Visible");
        verifyNoInteractions(genres);
        verifyNoMoreInteractions(movies, reviews, showtimes);
    }

    private MovieCardData card(int id, String title, LocalDate releaseDate) {
        return new MovieCardData(id, title, (short) 120, "/poster.jpg", "Synopsis", releaseDate,
                "T16", "https://youtu.be/test\n[COSMOS_FORMATS:2D,IMAX]");
    }

    private MovieReviewFormViewModel reviewForm(int movieId, String comment, String rating) {
        MovieReviewFormViewModel form = new MovieReviewFormViewModel();
        form.setMovieId(movieId);
        form.setComment(comment);
        form.setRating(new BigDecimal(rating));
        return form;
    }
}
