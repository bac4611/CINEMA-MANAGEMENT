package com.cinema.management.controller.account;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.repository.account.UserRepository;
import com.cinema.management.service.email.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "security.jwt.key=12345678901234567890123456789012",
        "security.jwt.issuer=cosmos-cinema",
        "security.jwt.audience=cosmos-cinema-api"
})
class AccountRegistrationLoginApiIntegrationTests {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @MockitoBean private EmailSender emailSender;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        when(emailSender.sendHtmlEmail(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void registerThenConfirmAndLoginReturnsUsableJwt() throws Exception {
        String email = "new.user@example.com";
        mvc.perform(post("/api/account/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "fullName": "Nguyen Van A",
                                  "phoneNumber": "0123456789",
                                  "email": "new.user@example.com",
                                  "dateOfBirth": "2000-01-01",
                                  "password": "Password!1",
                                  "confirmPassword": "Password!1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.email").value(email));

        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertNotEquals("Password!1", user.getPasswordHash());
        assertFalse(user.isEmailConfirmed());

        mvc.perform(post("/api/account/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"new.user@example.com","password":"Password!1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("chưa xác minh")));

        user.setEmailConfirmed(true);
        users.saveAndFlush(user);

        String loginBody = mvc.perform(post("/api/account/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"new.user@example.com","password":"Password!1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn().getResponse().getContentAsString();

        String token = loginBody.replaceAll(
                ".*\\\"accessToken\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*", "$1");
        mvc.perform(get("/api/account/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("KhachHang"));
    }

    @Test
    void registerRejectsInvalidPayloadAndDuplicateEmail() throws Exception {
        mvc.perform(post("/api/account/register")
                        .contentType("application/json")
                        .content("""
                                {"fullName":"A1","email":"bad","password":"short"}
                                """))
                .andExpect(status().isBadRequest());

        String valid = """
                {
                  "fullName":"Nguyen Van A",
                  "phoneNumber":"0123456789",
                  "email":"duplicate@example.com",
                  "dateOfBirth":"2000-01-01",
                  "password":"Password!1",
                  "confirmPassword":"Password!1"
                }
                """;
        mvc.perform(post("/api/account/register").contentType("application/json").content(valid))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/account/register").contentType("application/json").content(valid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("DUPLICATE_ACCOUNT"));
    }
}
