package com.cinema.management.controller.booking;

import com.cinema.management.model.viewmodel.booking.*;
import com.cinema.management.service.booking.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.*;
import java.net.http.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:booking_session_http;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
class BookingSessionHttpIntegrationTests {
    @LocalServerPort private int port;
    @MockitoBean private BookingService bookings;

    @BeforeEach
    void largeSchedule() {
        var page = new BookingSchedulePageViewModel();
        page.setToday(LocalDate.now()); page.setSelectedDate(LocalDate.now());
        List<BookingScheduleMovieViewModel> movies = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            var movie = new BookingScheduleMovieViewModel();
            movie.setMovieId(i); movie.setTitle("Large schedule movie " + i); movie.setPosterUrl("/poster.jpg");
            movie.setGenres("Action"); movie.setFormats(List.of("2D PHỤ ĐỀ"));
            var show = new BookingScheduleShowtimeViewModel();
            show.setShowtimeId(i); show.setMovieId(i); show.setDate(LocalDate.now().toString());
            show.setTime("22:00"); show.setFormat("2D PHỤ ĐỀ"); show.setAvailabilityLabel("100 ghế trống");
            movie.setShowtimes(List.of(show)); movies.add(movie);
        }
        page.setMovies(movies);
        when(bookings.getSchedule(any(), any())).thenReturn(page);
    }

    @Test
    void newVisitorGetsCompleteLargePageSessionAndUsableCsrfToken() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (var client = HttpClient.newBuilder().cookieHandler(cookies).build()) {
            var response = client.send(HttpRequest.newBuilder(uri("/booking")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            String html = response.body();
            assertTrue(html.length() > 32768, "Must exceed the servlet response buffer");
            assertTrue(html.contains("bookingScheduleForm"));
            assertTrue(html.contains("cosmos-site-footer"));
            assertTrue(html.contains("</html>"));
            assertTrue(cookies.getCookieStore().getCookies().stream().anyMatch(c -> c.getName().equals("JSESSIONID")));
            var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
            assertTrue(token.find(), "Rendered POST form must contain the CSRF token");
            var invalid = client.send(HttpRequest.newBuilder(uri("/booking/select-showtime-from-schedule"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("movieId=1&showtimeId=1&_csrf=invalid")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, invalid.statusCode(), "An existing session must not bypass CSRF validation");
            String form = "movieId=1&showtimeId=1&_csrf=" + URLEncoder.encode(token.group(1), java.nio.charset.StandardCharsets.UTF_8);
            var post = client.send(HttpRequest.newBuilder(uri("/booking/select-showtime-from-schedule"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(302, post.statusCode(), "Valid CSRF must reach the existing login check");
            assertTrue(post.headers().firstValue("Location").orElseThrow().endsWith("/account/login"));
            verify(bookings, never()).selectShowtime(anyInt(), any(), any(), anyBoolean());
        }
    }

    @Test
    void postingWithoutCsrfRemainsForbidden() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(uri("/booking/select-showtime-from-schedule"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("movieId=1&showtimeId=1")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, response.statusCode());
            verify(bookings, never()).selectShowtime(anyInt(), any(), any(), anyBoolean());
        }
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
}
