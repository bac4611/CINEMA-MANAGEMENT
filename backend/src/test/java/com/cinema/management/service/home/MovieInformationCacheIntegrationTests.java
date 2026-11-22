package com.cinema.management.service.home;

import com.cinema.management.model.dto.request.StaffMovieRequest;
import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.movie.Review;
import com.cinema.management.model.entity.theater.Room;
import com.cinema.management.model.entity.theater.Showtime;
import com.cinema.management.service.staff.StaffService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:movie_information_cache;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class MovieInformationCacheIntegrationTests {
    @Autowired HomeService home;
    @Autowired StaffService staff;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired WebApplicationContext context;
    private int movieId;
    private String title;
    private TransactionTemplate write;

    @BeforeEach
    void fixture() {
        write = new TransactionTemplate(transactionManager);
        title = "Cached movie " + UUID.randomUUID();
        movieId = write.execute(status -> {
            Movie movie = new Movie();
            movie.setTitle(title); movie.setDuration((short) 120);
            movie.setPosterUrl("/poster.jpg"); movie.setTrailer("https://youtu.be/test\n[COSMOS_FORMATS:2D,IMAX]");
            movie.setReleaseDate(LocalDate.now()); em.persist(movie);
            return movie.getMovieId();
        });
    }

    @Test
    void coldLoadUsesFiveQueriesAndWarmLoadUsesTwoWithoutSharingMutableViewModels() {
        var stats = resetStatistics();
        var first = home.getMovieDetails(movieId).orElseThrow();
        assertEquals(5, stats.getPrepareStatementCount());
        first.setTitle("Must not leak"); first.getReviewSummary().setTotalRatings(123);
        stats.clear();
        var next = home.getMovieDetails(movieId).orElseThrow();
        assertEquals(2, stats.getPrepareStatementCount());
        assertEquals(title, next.getTitle());
        assertEquals(0, next.getReviewSummary().getTotalRatings());
        assertEquals("https://youtu.be/test", next.getTrailer());
        assertEquals("2D,IMAX", next.getAvailableFormats());
    }

    @Test
    void warmCacheStillReadsNewReviewsAndSchedule() {
        home.getMovieDetails(movieId).orElseThrow();
        write.executeWithoutResult(status -> {
            Movie movie = em.find(Movie.class, movieId);
            User author = new User(); author.setFullName("Fresh author");
            author.setEmail(UUID.randomUUID() + "@example.com"); em.persist(author);
            Review review = new Review(); review.setMovie(movie); review.setUser(author);
            review.setContent("Fresh review"); review.setRating(new BigDecimal("4.50"));
            review.setStatus("Visible"); review.setCreatedAt(java.time.LocalDateTime.now()); em.persist(review);
            Room room = new Room(); room.setRoomName("Fresh hall"); em.persist(room);
            Showtime show = new Showtime(); show.setMovie(movie); show.setRoom(room);
            show.setDate(LocalDate.now().plusDays(1)); show.setStartTime(show.getDate().atTime(15, 0));
            show.setEndTime(show.getStartTime().plusHours(2)); show.setBasePrice(new BigDecimal("65000")); em.persist(show);
        });
        var stats = resetStatistics();
        var next = home.getMovieDetails(movieId).orElseThrow();
        assertEquals(2, stats.getPrepareStatementCount());
        assertEquals("Fresh review", next.getReviews().getFirst().getComment());
        assertEquals(new BigDecimal("4.50"), next.getAverageRating());
        assertEquals("Fresh hall", next.getShowtimes().getFirst().getRoomName());
    }

    @Test
    void successfulStaffEditEvictsAllMetadataIncludingCastDirectorAndGenresAfterCommit() {
        home.getMovieDetails(movieId).orElseThrow();
        var form = editForm();
        form.setTitle(title + " updated"); form.setDirector("Updated director");
        form.setCast("Updated actor"); form.setGenre("Updated genre");
        form.setLanguage("Updated language"); form.setCountry("Updated country");
        assertEquals(StaffService.Status.SUCCESS, staff.updateMovie(form).status());
        var stats = resetStatistics();
        var next = home.getMovieDetails(movieId).orElseThrow();
        assertEquals(5, stats.getPrepareStatementCount());
        assertEquals(form.getTitle(), next.getTitle());
        assertEquals("Updated director", next.getMovieDirector());
        assertEquals("Updated actor", next.getMovieCast());
        assertEquals("Updated genre", next.getGenre());
        assertEquals("Updated language", next.getLanguageName());
        assertEquals("Updated country", next.getCountryName());
        stats.clear(); home.getMovieDetails(movieId).orElseThrow();
        assertEquals(2, stats.getPrepareStatementCount());
    }

    @Test
    void rollbackKeepsOldCacheAndNeverPublishesUncommittedMetadata() {
        home.getMovieDetails(movieId).orElseThrow();
        write.executeWithoutResult(status -> {
            var form = editForm(); form.setTitle(title + " rolled back");
            assertEquals(StaffService.Status.SUCCESS, staff.updateMovie(form).status());
            assertEquals(form.getTitle(), home.getMovieDetails(movieId).orElseThrow().getTitle());
            status.setRollbackOnly();
        });
        var stats = resetStatistics();
        assertEquals(title, home.getMovieDetails(movieId).orElseThrow().getTitle());
        assertEquals(2, stats.getPrepareStatementCount());
    }

    @Test
    void successfulDeleteDoesNotLeaveCachedMovieAccessible() {
        home.getMovieDetails(movieId).orElseThrow();
        assertEquals(StaffService.Status.SUCCESS, staff.deleteMovie(movieId).status());
        var stats = resetStatistics();
        assertTrue(home.getMovieDetails(movieId).isEmpty());
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void rejectedEditKeepsExistingCache() {
        home.getMovieDetails(movieId).orElseThrow();
        var form = editForm(); form.setTitle("");
        assertEquals(StaffService.Status.INVALID, staff.updateMovie(form).status());
        var stats = resetStatistics();
        assertEquals(title, home.getMovieDetails(movieId).orElseThrow().getTitle());
        assertEquals(2, stats.getPrepareStatementCount());
    }

    @Test
    void warmPagesShareMetadataAcrossUrlVariantsButKeepFreshAccountAndCsrf() throws Exception {
        home.getMovieDetails(movieId).orElseThrow();
        int viewerId = write.execute(status -> {
            User viewer = new User(); viewer.setEmail(UUID.randomUUID() + "@example.com");
            viewer.setFullName("Viewer"); viewer.setRole("KhachHang"); viewer.setStatus(true);
            em.persist(viewer); return viewer.getUserId();
        });
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String path : List.of("/movies/details?id=" + movieId, "/movies/details/" + movieId)) {
            var stats = resetStatistics();
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(view().name("home/details"));
            assertEquals(2, stats.getPrepareStatementCount());
        }
        var stats = resetStatistics();
        String html = mvc.perform(get("/movies/details").param("id", "" + movieId)
                        .with(user("viewer").roles("KhachHang")).sessionAttr("UserID", viewerId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("data-review-form")); assertTrue(html.contains("name=\"_csrf\""));
        assertEquals(3, stats.getPrepareStatementCount());
        write.executeWithoutResult(status -> em.find(User.class, viewerId).setStatus(false));
        stats.clear();
        html = mvc.perform(get("/movies/details").param("id", "" + movieId)
                        .with(user("viewer").roles("KhachHang")).sessionAttr("UserID", viewerId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("data-review-form"));
        assertEquals(3, stats.getPrepareStatementCount());
    }

    private StaffMovieRequest editForm() {
        var form = new StaffMovieRequest(); form.setMovieId(movieId); form.setTitle(title);
        form.setDuration((short) 120); form.setPosterUrl("/poster.jpg");
        form.setTrailer("https://youtu.be/test"); form.setAvailableFormats(List.of("2D", "IMAX"));
        form.setReleaseDate(LocalDate.now()); return form;
    }

    private Statistics resetStatistics() {
        var stats = emf.unwrap(SessionFactory.class).getStatistics(); stats.clear(); return stats;
    }
}
