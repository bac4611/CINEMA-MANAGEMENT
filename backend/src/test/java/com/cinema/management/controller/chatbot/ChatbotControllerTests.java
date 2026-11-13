package com.cinema.management.controller.chatbot;

import com.cinema.management.model.dto.request.GenreRecommendationInput;
import com.cinema.management.model.dto.response.ChatbotResponse;
import com.cinema.management.service.chatbot.ChatbotService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatbotControllerTests {
    private final ChatbotService service = mock(ChatbotService.class);
    private final ChatbotController controller = new ChatbotController(service);

    @Test
    void missingChoiceReturnsBadRequestWithoutCallingService() {
        var response = controller.recommend(new GenreRecommendationInput("vui", "", "nhe_nhang", "P"));

        assertEquals(400, response.getStatusCode().value());
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("message"));
        verifyNoInteractions(service);
    }

    @Test
    void completeRequestReturnsRecommendation() {
        var request = new GenreRecommendationInput("vui", "tre_em", "nhe_nhang", "P");
        var expected = new ChatbotResponse("recommend_by_mood", "vui", "HoatHinh",
                "Hoạt hình", "reply", List.of());
        when(service.recommend(request)).thenReturn(expected);

        var response = controller.recommend(request);

        assertEquals(200, response.getStatusCode().value());
        assertSame(expected, response.getBody());
    }
}
