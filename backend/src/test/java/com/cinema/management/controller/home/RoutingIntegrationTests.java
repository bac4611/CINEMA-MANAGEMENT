package com.cinema.management.controller.home;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "sepay.webhook-api-key=routing-test-only")
class RoutingIntegrationTests {
    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // Snapshot of the endpoint inventory BEFORE migration, not derived from the
    // new annotations: catches missing routes and accidentally redirected POSTs.
    @ParameterizedTest
    @CsvFileSource(resources = "/routing-contract.csv", numLinesToSkip = 1)
    void canonicalAndCompatibleRoutesKeepTheirHandlers(String method, String oldPath,
                                                       String canonical, String policy) throws Exception {
        HandlerMethod expected = handler(method, canonical);
        assertNotEquals(LegacyRoutingController.class, expected.getBeanType());
        if (policy.equals("direct")) {
            assertEquals(expected.getMethod(), handler(method, oldPath).getMethod());
        } else if (method.equals("GET") && !oldPath.equals(canonical)) {
            assertEquals(LegacyRoutingController.class, handler(method, oldPath).getBeanType());
            mvc.perform(get(URI.create(oldPath + "?token=a%2Bb%2F%3D&userId=17"))
                            .with(user("routing").roles("Admin", "Staff", "KhachHang")))
                    .andExpect(status().isPermanentRedirect())
                    .andExpect(redirectedUrl(canonical + "?token=a%2Bb%2F%3D&userId=17"));
        }
    }

    private HandlerMethod handler(String method, String path) throws Exception {
        var request = new MockHttpServletRequest(context.getServletContext(), method, path);
        request.setServletPath(path);
        ServletRequestPathUtils.parseAndCache(request);
        var chain = mappings.getHandler(request);
        assertNotNull(chain, method + " " + path);
        return assertInstanceOf(HandlerMethod.class, chain.getHandler());
    }

    @Test
    void rootRedirectsOnceAndContextPathAndEncodedQuerySurviveLegacyRedirects() throws Exception {
        mvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/home"));
        mvc.perform(get("/home")).andExpect(status().isOk()).andExpect(view().name("home/index"));
        mvc.perform(get("/movies")).andExpect(status().isOk()).andExpect(view().name("home/movie"));
        mvc.perform(get("/cinema/").contextPath("/cinema"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/cinema/home"));
        mvc.perform(get(URI.create("/cinema/Account/ResetPassword?userId=17&token=a%2Bb%2F%3D"))
                        .contextPath("/cinema"))
                .andExpect(status().isPermanentRedirect())
                .andExpect(redirectedUrl("/cinema/account/reset-password?userId=17&token=a%2Bb%2F%3D"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/home", "/movies", "/ticket-pricing", "/account/login", "/account/register",
            "/account/forgot-password", "/account/forgot-password-confirmation", "/account/reset-password",
            "/account/reset-password-confirmation", "/account/email-verification-sent", "/account/access-denied", "/booking"})
    void publicPagesRenderAtCanonicalUrls(String path) throws Exception {
        mvc.perform(get(path).param("userId", "17")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/analytics", "/admin/staff", "/staff", "/staff/halls", "/staff/concessions"})
    void managementPagesRenderForAuthorizedUser(String path) throws Exception {
        mvc.perform(get(path).with(user("admin").roles("Admin"))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/settings", "/staff", "/account/profile", "/booking/payment-status",
            "/booking/payment-success", "/Admin/Index", "/Staff/Halls", "/Account/Profile"})
    void privatePagesRequireAuthentication(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isFound())
                .andExpect(redirectedUrl("/account/login"));
    }

    @Test
    void customerCannotAccessManagementAndReviewAndPaymentRolesStayProtected() throws Exception {
        mvc.perform(get("/admin").with(user("customer").roles("KhachHang")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/staff").with(user("customer").roles("KhachHang")))
                .andExpect(status().isForbidden());
        for (String path : new String[]{"/movies/add-review", "/booking/cancel-booking", "/booking/complete-dev-payment"}) {
            mvc.perform(post(path).with(user("staff").roles("Staff")).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path).with(user("customer").roles("KhachHang")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void sensitiveLegacyPostsExecuteDirectlyAndRemovedStaffPostsNeverRedirect() throws Exception {
        mvc.perform(post("/Account/Logout").with(csrf()))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/account/login"));
        mvc.perform(post("/Booking/StartPayment").with(csrf()))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/account/login"));
        mvc.perform(post("/Staff/SaveSchedule").with(user("staff").roles("Staff")).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound()).andExpect(header().doesNotExist("Location"));
    }

    @ParameterizedTest
    @CsvSource({"/booking/select-showtime,/account/login", "/booking/select-seats,/booking",
            "/booking/select-concessions,/booking/select-seats", "/booking/checkout,/booking",
            "/booking/payment,/account/login", "/booking/payment-success,/account/login", "/account/profile,/account/login"})
    void missingBookingSessionUsesTheExistingCanonicalRecoveryDestination(String path, String destination) throws Exception {
        mvc.perform(get(path).param("movieId", "17").param("bookingId", "17")
                        .with(user("customer").roles("KhachHang")))
                .andExpect(status().isFound()).andExpect(redirectedUrl(destination));
    }

    @Test
    void sepayStillAcceptsPostWithoutCsrfAndChecksItsApiKey() throws Exception {
        mvc.perform(post("/webhooks/sepay").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
    }
}
