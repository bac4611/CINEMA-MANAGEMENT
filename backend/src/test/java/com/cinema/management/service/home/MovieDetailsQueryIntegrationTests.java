package com.cinema.management.service.home;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.movie.*;
import com.cinema.management.model.entity.theater.Room;
import com.cinema.management.model.entity.theater.Showtime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:movie_detail_queries;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class MovieDetailsQueryIntegrationTests {
    @Autowired private HomeService home;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private WebApplicationContext context;

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 40})
    void detailQueryCountRemainsFiveWithMoreRoomsAuthorsAndReviews(int extraRows) {
        Movie movie = fixture();
        for (int i = 0; i < extraRows; i++) {
            addShowtime(movie, "Room " + i, LocalDate.now().plusDays(i % 7), 12, i);
            addReview(movie, addUser("Reviewer " + i, "reviewer" + i + "@example.com"),
                    "4.00", "Visible", null, LocalDateTime.now().plusMinutes(i));
        }

        Statistics stats = resetStatistics();
        var details = home.getMovieDetails(movie.getMovieId()).orElseThrow();
        assertEquals(2 + extraRows, details.getShowtimes().size());
        assertEquals(2 + extraRows, details.getReviews().size());
        assertEquals(2 + extraRows, details.getReviewCount());
        assertEquals("Việt Nam", details.getCountryName());
        assertEquals("Tiếng Việt", details.getLanguageName());
        assertEquals(5, stats.getPrepareStatementCount(), "No per-room/per-author/per-genre lazy queries");
    }

    @Test
    void detailsPreserveMetadataReviewFiltersRoundingAndVisibleSchedule() {
        Movie movie = fixture();
        Statistics stats = resetStatistics();

        var details = home.getMovieDetails(movie.getMovieId()).orElseThrow();

        assertEquals("Details query movie", details.getTitle());
        assertEquals("Synopsis", details.getSynopsis());
        assertEquals("/poster.jpg", details.getPosterUrl());
        assertEquals("T16", details.getAgeRating());
        assertEquals(120, details.getDuration());
        assertEquals("https://youtu.be/test", details.getTrailer());
        assertEquals("2D,IMAX", details.getAvailableFormats());
        assertEquals("Việt Nam", details.getCountryName());
        assertEquals("Tiếng Việt", details.getLanguageName());
        assertNotNull(details.getCountryId());
        assertNotNull(details.getLanguageId());
        assertEquals(2, details.getGenre().split(", ").length);
        assertTrue(details.getGenre().contains("Drama"));
        assertTrue(details.getGenre().contains("Action"));
        assertEquals("Director Alpha, Director Zulu", details.getMovieDirector());
        assertEquals("Actor Alpha, Actor Zulu", details.getMovieCast());

        assertEquals(2, details.getReviewCount());
        assertEquals(new BigDecimal("2.01"), details.getAverageRating());
        assertEquals(new BigDecimal("2.01"), details.getReviewSummary().getAverageRating());
        assertEquals(List.of("Zero rating", "Rated review"),
                details.getReviews().stream().map(review -> review.getComment()).toList());
        assertEquals(List.of("Reviewer Two", "Reviewer One"),
                details.getReviews().stream().map(review -> review.getUserFullName()).toList());

        // Preserve all times on today, including an earlier time: the existing UI
        // filters by calendar day, not whether the screening has started.
        assertEquals(List.of(LocalDate.now().toString(), LocalDate.now().plusDays(6).toString()),
                details.getShowtimes().stream().map(show -> show.getDate()).toList());
        assertEquals(List.of("00:15", "22:00"), details.getShowtimes().stream().map(show -> show.getTime()).toList());
        assertEquals(List.of("2D", "IMAX 2D"), details.getShowtimes().stream().map(show -> show.getFormat()).toList());
        assertEquals(5, stats.getPrepareStatementCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/movies/details", "/movies/details/{id}"})
    void anonymousDetailPagesRenderAllContentUsingFiveStatements(String path) throws Exception {
        Movie movie = fixture();
        Statistics stats = resetStatistics();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String html = mvc.perform(get(path.replace("{id}", movie.getMovieId().toString()))
                        .param("id", movie.getMovieId().toString()))
                .andExpect(status().isOk()).andExpect(view().name("home/details"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String expected : List.of("Details query movie", "Việt Nam", "Tiếng Việt", "Drama", "Action",
                "Director Alpha", "Actor Alpha", "Rated review", "Zero rating", "Reviewer One", "Reviewer Two",
                "00:15", "22:00", "/booking/select-showtime", "cosmos-site-footer")) {
            assertTrue(html.contains(expected), "Page must still display " + expected);
        }
        assertFalse(html.contains("Hidden review"));
        assertFalse(html.contains("Deleted review"));
        assertFalse(html.contains("Reply review"));
        assertFalse(html.contains("No rating review"));
        assertFalse(html.contains("data-review-form"));
        assertEquals(5, stats.getPrepareStatementCount(), "Template rendering must not add SQL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"KhachHang", "Staff"})
    void loggedInPagesKeepTheFreshAccountStatusAndRoleCheck(String role) throws Exception {
        Movie movie = fixture();
        // Different from the review authors so the account status lookup is a
        // separate query even inside this test's shared transaction.
        User viewer = addUser("Current viewer", "viewer@example.com");
        viewer.setRole(role);
        Statistics stats = resetStatistics();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String html = mvc.perform(get("/movies/details").param("id", movie.getMovieId().toString())
                        .with(user(viewer.getEmail()).roles(role)).sessionAttr("UserID", viewer.getUserId()))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(role.equals("KhachHang"), html.contains("data-review-form"));
        if (role.equals("KhachHang")) assertTrue(html.contains("action=\"/movies/add-review\""));
        assertEquals(6, stats.getPrepareStatementCount(), "Five content queries plus the account status check");
    }

    @Test
    void missingMovieStopsAfterOneQuery() {
        Statistics stats = resetStatistics();
        assertTrue(home.getMovieDetails(Integer.MAX_VALUE).isEmpty());
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void optionalMetadataAndEmptyReviewsOrScheduleRemainSupported() {
        Movie movie = addMovie("Sparse movie");
        Statistics stats = resetStatistics();
        var details = home.getMovieDetails(movie.getMovieId()).orElseThrow();
        assertEquals("", details.getCountryName());
        assertEquals("", details.getLanguageName());
        assertEquals("", details.getGenre());
        assertEquals("", details.getMovieCast());
        assertEquals("", details.getMovieDirector());
        assertTrue(details.getShowtimes().isEmpty());
        assertTrue(details.getReviews().isEmpty());
        assertEquals(0, details.getReviewCount());
        assertEquals(BigDecimal.ZERO, details.getAverageRating());
        assertEquals(5, stats.getPrepareStatementCount());
    }

    private Movie fixture() {
        Country country = new Country(); country.setCountryName("Việt Nam"); entityManager.persist(country);
        Language language = new Language(); language.setLanguageName("Tiếng Việt"); entityManager.persist(language);
        Movie movie = addMovie("Details query movie");
        movie.setCountry(country); movie.setLanguage(language);
        for (String name : List.of("Drama", "Action")) {
            Genre genre = new Genre(); genre.setName(name); entityManager.persist(genre);
            MovieGenre link = new MovieGenre(); link.setMovieId(movie.getMovieId()); link.setGenreId(genre.getGenreId());
            entityManager.persist(link);
        }
        for (String name : List.of("Director Zulu", "Director Alpha", "Actor Zulu", "Actor Alpha")) {
            Person person = new Person(); person.setFullName(name); entityManager.persist(person);
            if (name.startsWith("Director")) {
                MovieDirector link = new MovieDirector(); link.setMovieId(movie.getMovieId()); link.setPersonId(person.getPersonId());
                entityManager.persist(link);
            } else {
                MovieCast link = new MovieCast(); link.setMovieId(movie.getMovieId()); link.setPersonId(person.getPersonId());
                entityManager.persist(link);
            }
        }
        LocalDate today = LocalDate.now();
        addShowtime(movie, "Standard Hall", today, 0, 15);
        addShowtime(movie, "IMAX Hall", today.plusDays(6), 22, 0);
        addShowtime(movie, "Yesterday Hall", today.minusDays(1), 13, 0);
        addShowtime(movie, "Next Week Hall", today.plusDays(7), 13, 0);
        addShowtime(addMovie("Other movie"), "Other Hall", today, 13, 0);
        User one = addUser("Reviewer One", "one@example.com");
        User two = addUser("Reviewer Two", "two@example.com");
        LocalDateTime time = today.atTime(12, 0);
        var parent = addReview(movie, one, "4.01", "Visible", null, time); parent.setContent("Rated review");
        addReview(movie, two, "0.00", "Visible", null, time.plusMinutes(1)).setContent("Zero rating");
        addReview(movie, one, "5.00", "Hidden", null, time.plusMinutes(2)).setContent("Hidden review");
        addReview(movie, one, "5.00", "Deleted", null, time.plusMinutes(3)).setContent("Deleted review");
        addReview(movie, one, "5.00", "Visible", parent, time.plusMinutes(4)).setContent("Reply review");
        addReview(movie, one, null, "Visible", null, time.plusMinutes(5)).setContent("No rating review");
        return movie;
    }

    private Movie addMovie(String title) {
        Movie movie = new Movie(); movie.setTitle(title); movie.setDuration((short) 120);
        movie.setPosterUrl("/poster.jpg"); movie.setSynopsis("Synopsis"); movie.setAgeRating("T16");
        movie.setTrailer("https://youtu.be/test\n[COSMOS_FORMATS:2D,IMAX]"); movie.setReleaseDate(LocalDate.now());
        entityManager.persist(movie);
        return movie;
    }

    private void addShowtime(Movie movie, String roomName, LocalDate date, int hour, int minute) {
        Room room = new Room(); room.setRoomName(roomName); entityManager.persist(room);
        Showtime showtime = new Showtime(); showtime.setMovie(movie); showtime.setRoom(room); showtime.setDate(date);
        showtime.setStartTime(date.atTime(hour, minute)); showtime.setEndTime(showtime.getStartTime().plusHours(2));
        showtime.setBasePrice(BigDecimal.valueOf(65000)); entityManager.persist(showtime);
    }

    private User addUser(String name, String email) {
        User user = new User(); user.setFullName(name); user.setEmail(email); user.setRole("KhachHang");
        entityManager.persist(user);
        return user;
    }

    private Review addReview(Movie movie, User user, String rating, String status, Review parent, LocalDateTime time) {
        Review review = new Review(); review.setMovie(movie); review.setUser(user); review.setContent("Additional review");
        review.setRating(rating == null ? null : new BigDecimal(rating)); review.setStatus(status);
        review.setParentReview(parent); review.setCreatedAt(time); entityManager.persist(review);
        return review;
    }

    private Statistics resetStatistics() {
        entityManager.flush();
        entityManager.clear();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        return stats;
    }
}
