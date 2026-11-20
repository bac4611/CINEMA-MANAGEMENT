package com.cinema.management.controller.account;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.service.account.AccountService;
import com.cinema.management.service.authentication.JwtTokenResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.net.URI;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "security.jwt.key=12345678901234567890123456789012",
        "security.jwt.issuer=cosmos-cinema",
        "security.jwt.audience=cosmos-cinema-api"
})
class AccountControllerTests {
    @Autowired private WebApplicationContext context;
    @MockitoBean private AccountService accounts;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void jsonLoginPreservesResponseAndNeverEnablesPasswordlessLogin() throws Exception {
        User user = new User();
        user.setUserId(42);
        user.setFullName("Nguyen Van A");
        user.setEmail("a@example.com");
        user.setRole("KhachHang");
        user.setPasswordHash("must-not-be-returned");
        when(accounts.authenticate(any(), eq(false)))
                .thenReturn(new AccountService.LoginResult(AccountService.LoginStatus.SUCCESS, user));
        when(accounts.generateAccessToken(user)).thenReturn(new JwtTokenResult("signed-token", "Bearer", Instant.now()));
        mvc.perform(post("/api/account/login").contentType("application/json")
                        .content(""" 
                                {"email":"a@example.com","password":"Password!1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.userId").value(42))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
        verify(accounts).authenticate(any(), eq(false));
    }

    @Test
    void jsonLoginRejectsMissingPasswordAndInvalidCredentials() throws Exception {
        mvc.perform(post("/login").contentType("application/json")
                        .content(""" 
                                {"email":"admin@gmail.com","password":""}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accounts);
        when(accounts.authenticate(any(), eq(false)))
                .thenReturn(new AccountService.LoginResult(AccountService.LoginStatus.INVALID_CREDENTIALS, null));
        mvc.perform(post("/login").contentType("application/json")
                        .content(""" 
                                {"email":"a@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRequiresBearerTokenEvenWhenSessionAuthenticationExists() throws Exception {
        mvc.perform(get("/api/account/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/account/me").with(user("a@example.com").roles("KhachHang")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedJwtIsValidatedForSignatureAudienceRoleAndExpiry() throws Exception {
        mvc.perform(get("/api/account/me").header("Authorization", "Bearer " + token("KhachHang", "cosmos-cinema-api", 300)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(42));
        mvc.perform(get("/api/account/me").header("Authorization", "Bearer " + token("Other", "cosmos-cinema-api", 300)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/account/me").header("Authorization", "Bearer " + token("KhachHang", "wrong-audience", 300)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/account/me").header("Authorization", "Bearer " + token("KhachHang", "cosmos-cinema-api", -300)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/account/me").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mvcFormsRequireCsrfAndProfileRequiresAuthentication() throws Exception {
        mvc.perform(post("/account/logout")).andExpect(status().isForbidden());
        mvc.perform(post("/account/logout").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/account/login"));
        mvc.perform(get("/account/profile"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void mvcLoginCreatesSessionAndRedirectsByRole() throws Exception {
        User user = new User();
        user.setUserId(42);
        user.setEmail("admin@gmail.com");
        user.setFullName("Admin");
        user.setRole("Admin");
        when(accounts.isDevelopmentPasswordlessLogin(user.getEmail())).thenReturn(true);
        when(accounts.authenticate(any(), eq(true)))
                .thenReturn(new AccountService.LoginResult(AccountService.LoginStatus.SUCCESS, user));
        mvc.perform(post("/account/login").with(csrf())
                        .param("email", user.getEmail()).param("password", ""))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin"))
                .andExpect(request().sessionAttribute("UserID", 42))
                .andExpect(request().sessionAttribute("UserRole", "Admin"));
    }

    @Test
    void forgotPasswordBuildsCanonicalEmailLinkAndRedirectsToConfirmation() throws Exception {
        doAnswer(invocation -> {
            BiFunction<Integer, String, String> link = invocation.getArgument(1);
            assertEquals("http://localhost/account/reset-password?userId=42&token=email-token",
                    link.apply(42, "email-token"));
            return null;
        }).when(accounts).forgotPassword(eq("a@example.com"), any());
        mvc.perform(post("/account/forgot-password").with(csrf()).param("email", "a@example.com"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/account/forgot-password-confirmation"));
        verify(accounts).forgotPassword(eq("a@example.com"), any());
    }

    @Test
    void webAndApiRegistrationBuildCanonicalVerificationEmailLinks() throws Exception {
        when(accounts.register(any(), any())).thenAnswer(invocation -> {
            BiFunction<Integer, String, String> link = invocation.getArgument(1);
            assertEquals("/account/confirm-email", URI.create(link.apply(42, "email-token")).getPath());
            assertEquals("userId=42&token=email-token", URI.create(link.apply(42, "email-token")).getQuery());
            return new AccountService.RegistrationResult(AccountService.RegistrationStatus.CREATED, "a@example.com");
        });
        mvc.perform(post("/account/register").with(csrf()).param("email", "a@example.com")
                        .param("fullName", "Nguyen Van A").param("phoneNumber", "0901234567")
                        .param("dateOfBirth", "2000-01-01").param("password", "Password1!")
                        .param("confirmPassword", "Password1!"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/account/email-verification-sent?email=a%40example.com"));
        mvc.perform(post("/api/account/register").contentType("application/json").content("""
                {"email":"a@example.com","fullName":"Nguyen Van A","phoneNumber":"0901234567",
                 "dateOfBirth":"2000-01-01","password":"Password1!","confirmPassword":"Password1!"}
                """))
                .andExpect(status().isCreated());
        verify(accounts, times(2)).register(any(), any());
    }

    private String token(String role, String audience, int expiresInSeconds) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("cosmos-cinema").audience(List.of(audience))
                .subject("42").issuedAt(now.minusSeconds(600)).expiresAt(now.plusSeconds(expiresInSeconds))
                .claim("name", "Nguyen Van A").claim("email", "a@example.com").claim("role", role).build();
        var key = new SecretKeySpec("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtEncoder.withSecretKey(key).build().encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
