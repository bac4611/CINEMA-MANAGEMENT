package com.cinema.management.controller.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles({"test", "google"})
@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=cinema-test.apps.googleusercontent.com",
        "GOOGLE_CLIENT_SECRET=test-only-not-a-real-secret"
})
class GoogleLoginConfigurationTests {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void loginPageOffersGoogleAndPublicPostStartsAuthorization() throws Exception {
        mvc.perform(get("/account/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/google-auth/login\"")));
        mvc.perform(post("/google-auth/login").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/google"));
    }

    @Test
    void authorizationUsesGoogleAndTheRegisteredLocalRedirectUri() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://accounts.google.com/")))
                .andExpect(header().string("Location", containsString("client_id=cinema-test.apps.googleusercontent.com")))
                .andExpect(header().string("Location", containsString("redirect_uri=http://localhost/login/oauth2/code/google")))
                .andExpect(header().string("Location", containsString("state=")));
        mvc.perform(post("/google-auth/login"))
                .andExpect(status().isForbidden());
    }
}
