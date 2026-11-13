package com.cinema.management.controller.account;

import com.cinema.management.service.authentication.GoogleAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "security.jwt.key=12345678901234567890123456789012",
        "security.jwt.issuer=cosmos-cinema",
        "security.jwt.audience=cosmos-cinema-api"
})
class GoogleAuthSecurityTests {
    @Autowired private WebApplicationContext context;
    @MockitoBean private GoogleAuthService google;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void loginEndpointIsPublicButStillRequiresCsrf() throws Exception {
        mvc.perform(post("/GoogleAuth/Login"))
                .andExpect(status().isForbidden());
        when(google.isEnabled()).thenReturn(false);
        mvc.perform(post("/GoogleAuth/Login").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/Account/Login"));
    }
}
