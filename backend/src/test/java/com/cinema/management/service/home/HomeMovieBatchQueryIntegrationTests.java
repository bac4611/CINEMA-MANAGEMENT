package com.cinema.management.service.home;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.movie.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:home_batch_queries;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class HomeMovieBatchQueryIntegrationTests {
    @Autowired private HomeService home;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private WebApplicationContext context;

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 100})
    void bothPagesUseThreeSqlStatementsAsTheMovieCountGrows(int movieCount) {
        for (int i = 0; i < movieCount; i++) persistMovie("Movie " + i);

        Statistics stats = resetStatistics();
        var cards = home.getHomeMovies();
        assertEquals(movieCount, cards.size());
        assertEquals(3, stats.getPrepareStatementCount(), "Home SQL count for " + movieCount + " movies");
        assertEquals(0, stats.getEntityLoadCount(), "Cards must not hydrate entity relationships");

        stats = resetStatistics();
        var catalog = home.getMovieCatalog();
        assertEquals(movieCount, catalog.nowShowing().size());
        assertEquals(Math.min(4, movieCount), catalog.featured().size());
        assertEquals(3, stats.getPrepareStatementCount(), "Movies SQL count for " + movieCount + " movies");
        assertEquals(0, stats.getEntityLoadCount());
    }

    @Test
    void groupedRatingsPreserveVisibilityRootFilterZeroRatingsAndHalfUpRounding() {
        Movie movie = persistMovie("Rated movie");
        Movie unrated = persistMovie("Unrated movie");
        User user = new User();
        user.setFullName("Batch query tester");
        user.setEmail("batch-query@example.com");
        user.setRole("KhachHang");
        entityManager.persist(user);

        Genre drama = persistGenre("Drama");
        Genre action = persistGenre("Action");
        linkGenre(movie, drama);
        linkGenre(movie, action);
        Review parent = persistReview(movie, user, "4.01", "Visible", null);
        persistReview(movie, user, "0.00", "Visible", null);
        persistReview(movie, user, "5.00", "Hidden", null);
        persistReview(movie, user, "5.00", "Deleted", null);
        persistReview(movie, user, "5.00", "Visible", parent);
        persistReview(movie, user, null, "Visible", null);

        Statistics stats = resetStatistics();
        var cards = home.getHomeMovies();
        var rated = cards.stream().filter(card -> card.getMovieId() == movie.getMovieId()).findFirst().orElseThrow();
        assertEquals("Drama, Action", rated.getGenre());
        assertEquals(new BigDecimal("2.01"), rated.getAverageRating());
        assertEquals(2, rated.getReviewCount());
        assertEquals(2, rated.getReviewSummary().getTotalRatings());
        var noRating = cards.stream().filter(card -> card.getMovieId() == unrated.getMovieId()).findFirst().orElseThrow();
        assertEquals("", noRating.getGenre());
        assertEquals(BigDecimal.ZERO, noRating.getAverageRating());
        assertEquals(0, noRating.getReviewCount());
        assertEquals(3, stats.getPrepareStatementCount());
        assertEquals(0, stats.getEntityLoadCount());
    }

    @Test
    void emptyPagesOnlyQueryTheMovieTable() {
        Statistics stats = resetStatistics();
        assertTrue(home.getHomeMovies().isEmpty());
        assertEquals(1, stats.getPrepareStatementCount());
        stats = resetStatistics();
        var catalog = home.getMovieCatalog();
        assertTrue(catalog.featured().isEmpty());
        assertTrue(catalog.nowShowing().isEmpty());
        assertTrue(catalog.comingSoon().isEmpty());
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/home", "/movies"})
    void realPagesRenderCardDataAndRatingsWithoutAdditionalSql(String path) throws Exception {
        Movie movie = persistMovie("Batch UI movie");
        linkGenre(movie, persistGenre("Drama"));
        User user = new User();
        user.setFullName("UI test customer");
        user.setEmail("batch-ui@example.com");
        user.setRole("KhachHang");
        entityManager.persist(user);
        persistReview(movie, user, "4.50", "Visible", null);

        Statistics stats = resetStatistics();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String html = mvc.perform(get(path)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("Batch UI movie"));
        assertTrue(html.contains("Drama"));
        assertTrue(html.contains("/poster.jpg"));
        assertTrue(java.util.regex.Pattern.compile(">4[.,]5(?:/5)?<").matcher(html).find(), "Rating remains visible");
        assertTrue(html.contains("/booking/select-showtime"), "Booking links must remain available");
        assertEquals(3, stats.getPrepareStatementCount(), "Rendering must not add lazy queries");
        assertEquals(0, stats.getEntityLoadCount());
    }

    private Statistics resetStatistics() {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    private Movie persistMovie(String title) {
        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setDuration((short) 120);
        movie.setPosterUrl("/poster.jpg");
        movie.setTrailer("https://youtu.be/test");
        movie.setReleaseDate(LocalDate.now());
        entityManager.persist(movie);
        return movie;
    }

    private Genre persistGenre(String name) {
        Genre genre = new Genre();
        genre.setName(name);
        entityManager.persist(genre);
        return genre;
    }

    private void linkGenre(Movie movie, Genre genre) {
        MovieGenre link = new MovieGenre();
        link.setMovieId(movie.getMovieId());
        link.setGenreId(genre.getGenreId());
        entityManager.persist(link);
    }

    private Review persistReview(Movie movie, User user, String rating, String status, Review parent) {
        Review review = new Review();
        review.setMovie(movie);
        review.setUser(user);
        review.setContent("Batch query test");
        review.setRating(rating == null ? null : new BigDecimal(rating));
        review.setStatus(status);
        review.setParentReview(parent);
        review.setCreatedAt(LocalDateTime.now());
        entityManager.persist(review);
        return review;
    }
}
