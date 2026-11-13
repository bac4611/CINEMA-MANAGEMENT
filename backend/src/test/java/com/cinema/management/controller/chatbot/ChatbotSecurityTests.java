package com.cinema.management.controller.chatbot;

import com.cinema.management.model.dto.response.ChatbotResponse;
import com.cinema.management.service.chatbot.ChatbotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.jwt.key=12345678901234567890123456789012",
        "security.jwt.issuer=cosmos-cinema",
        "security.jwt.audience=cosmos-cinema-api"
})
class ChatbotSecurityTests {
    @Autowired private WebApplicationContext context;
    @MockitoBean private ChatbotService chatbot;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void recommendationEndpointIsPublicAndDoesNotRequireCsrf() throws Exception {
        when(chatbot.recommend(any())).thenReturn(new ChatbotResponse(
                "recommend_by_mood", "vui", "HoatHinh", "Hoạt hình", "reply", List.of()));

        mvc.perform(post("/api/chatbot/recommend")
                        .contentType("application/json")
                        .content("""
                                {"mood":"vui","companion":"tre_em","intensity":"nhe_nhang","ageRating":"P"}
                                """))
                .andExpect(status().isOk());
    }
}
