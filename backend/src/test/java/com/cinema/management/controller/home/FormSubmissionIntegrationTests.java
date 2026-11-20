package com.cinema.management.controller.home;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.viewmodel.home.MovieViewModel;
import com.cinema.management.service.account.AccountService;
import com.cinema.management.service.home.HomeService;
import com.cinema.management.service.staff.StaffService;
import com.cinema.management.model.viewmodel.staff.StaffScheduleViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// A real servlet container is necessary: MockMvc does not execute the security
// AccessDeniedHandler's forward to the error page, which previously hid 403 as 405.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FormSubmissionIntegrationTests {
    @LocalServerPort private int port;
    @MockitoBean private HomeService home;
    @MockitoBean private AccountService accounts;
    @MockitoBean private StaffService staff;
    private HttpClient client;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var movie = new MovieViewModel();
        movie.setMovieId(7);
        movie.setTitle("Routing test movie");
        movie.setReleaseDate(LocalDate.of(2026, 1, 1));
        when(home.getMovieDetails(7)).thenReturn(Optional.of(movie));
        when(home.isActiveUser(42)).thenReturn(true);
        when(staff.getSchedulePage()).thenReturn(new StaffScheduleViewModel());
    }

    @Test
    void missingCsrfProduces403RatherThan405DuringSecurityForward() throws Exception {
        var response = post("/movies/add-review", "movieId=7&rating=4&comment=Test");
        assertEquals(403, response.statusCode());
        assertTrue(response.body().contains("Phiên gửi biểu mẫu đã hết hạn hoặc không hợp lệ"));
        verify(home, never()).addReview(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Staff"})
    void managementUsersDoNotSeeReviewFormAndCannotSubmitEvenWithValidCsrf(String role) throws Exception {
        signIn(role);
        var details = get("/movies/details?id=7");
        assertEquals(200, details.statusCode());
        assertFalse(details.body().contains("data-review-form"));
        assertTrue(details.body().contains("Chỉ tài khoản khách hàng được gửi đánh giá"));
        var response = post("/movies/add-review", "movieId=7&rating=4&comment=Test&_csrf=" + encode(csrf(details.body())));
        assertEquals(403, response.statusCode());
        verify(home, never()).addReview(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/movies/add-review", "/Home/AddReview"})
    void customerSubmissionUsesPostAndRedirectsBackToMovieDetails(String path) throws Exception {
        signIn("KhachHang");
        var details = get("/movies/details?id=7");
        assertEquals(200, details.statusCode());
        assertTrue(details.body().contains("data-review-form"));
        assertTrue(details.body().contains("action=\"/movies/add-review\""));
        when(home.addReview(eq(42), any())).thenReturn(
                new HomeService.ReviewResult(HomeService.ReviewStatus.SUCCESS, 7, "Đã gửi đánh giá."));
        var response = post(path, "movieId=7&rating=4&comment=Test&_csrf=" + encode(csrf(details.body())));
        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/movies/details?id=7"));
        verify(home).addReview(eq(42), argThat(form -> form.getMovieId() == 7
                && form.getRating().intValue() == 4 && form.getComment().equals("Test")));
    }

    @Test
    void customerWithMissingCsrfIsStillRejectedWithoutSaving() throws Exception {
        signIn("KhachHang");
        assertEquals(403, post("/movies/add-review", "movieId=7&rating=4&comment=Test").statusCode());
        verify(home, never()).addReview(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Staff", "Admin"})
    void movieEditFormIncludesCsrfAndSavesByPostForBothManagementRoles(String role) throws Exception {
        signIn(role);
        String page = get("/staff").body();
        String editForm = form(page, "/staff/edit-movie");
        assertTrue(editForm.contains("method=\"post\""));
        String token = csrf(editForm);
        when(staff.updateMovie(any())).thenReturn(
                new StaffService.OperationResult(StaffService.Status.SUCCESS, "Đã cập nhật phim.", 1, 0, 1, 0));
        var response = post("/staff/edit-movie", "movieId=7&title=Edited+movie&duration=120&_csrf=" + encode(token));
        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/staff"));
        verify(staff).updateMovie(argThat(movie -> movie.getMovieId() == 7
                && movie.getTitle().equals("Edited movie") && movie.getDuration() == 120));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/staff/edit-movie", "/account/logout"})
    void missingOrExpiredCsrfDoesNotBecome405OrExecuteSensitiveActions(String path) throws Exception {
        signIn("Staff");
        for (String csrfParameter : new String[]{"", "&_csrf=expired-token"}) {
            var response = post(path, "movieId=7&title=Edited+movie&duration=120" + csrfParameter);
            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("Phiên gửi biểu mẫu đã hết hạn hoặc không hợp lệ"));
        }
        verify(staff, never()).updateMovie(any());
        // A denied logout must leave the authenticated session intact.
        assertEquals(200, get("/staff").statusCode());
    }

    @Test
    void customerCannotEditMoviesEvenWithValidCsrf() throws Exception {
        signIn("KhachHang");
        String token = csrf(get("/movies/details?id=7").body());
        var response = post("/staff/edit-movie", "movieId=7&title=Edited+movie&duration=120&_csrf=" + encode(token));
        assertEquals(403, response.statusCode());
        assertTrue(response.body().contains("Tài khoản của bạn không có quyền truy cập trang này"));
        verify(staff, never()).updateMovie(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Staff", "Admin", "KhachHang"})
    void logoutFormUsesPostAndEndsTheAuthenticatedSession(String role) throws Exception {
        signIn(role);
        var page = get(role.equals("KhachHang") ? "/movies/details?id=7" : "/staff");
        String logout = form(page.body(), "/account/logout");
        assertTrue(logout.contains("method=\"post\""));
        var response = post("/account/logout", "_csrf=" + encode(csrf(logout)));
        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/account/login"));
        assertEquals(302, get("/account/profile").statusCode());
        assertEquals(302, get("/staff").statusCode());
    }

    private String form(String page, String action) {
        var match = Pattern.compile("<form\\b[^>]*action=\"" + Pattern.quote(action) + "\"[^>]*>.*?</form>",
                Pattern.DOTALL).matcher(page);
        assertTrue(match.find(), "Expected rendered form for " + action);
        return match.group();
    }

    private void signIn(String role) throws Exception {
        var user = new User();
        user.setUserId(42);
        user.setEmail("review@example.com");
        user.setFullName("Review Tester");
        user.setRole(role);
        when(accounts.authenticate(any(), eq(true))).thenReturn(
                new AccountService.LoginResult(AccountService.LoginStatus.SUCCESS, user));
        String token = csrf(get("/account/login").body());
        var response = post("/account/login", "email=review%40example.com&password=Password1%21&_csrf=" + encode(token));
        assertEquals(302, response.statusCode(), "Test account should sign in before submitting a review");
    }

    private String csrf(String html) {
        var match = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(match.find(), "Rendered POST form must contain a CSRF token");
        return match.group(1);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
