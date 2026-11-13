package com.cinema.management.controller.chatbot;

import com.cinema.management.model.dto.request.GenreRecommendationInput;
import com.cinema.management.model.dto.response.ChatbotResponse;
import com.cinema.management.service.chatbot.ChatbotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {
    private static final String REQUIRED_FIELDS_MESSAGE =
            "Vui lòng chọn đầy đủ tâm trạng, người đi cùng, nhịp phim và độ tuổi.";

    private final ChatbotService chatbot;

    public ChatbotController(ChatbotService chatbot) {
        this.chatbot = chatbot;
    }

    @PostMapping("/recommend")
    public ResponseEntity<?> recommend(@RequestBody(required = false) GenreRecommendationInput request) {
        if (request == null || blank(request.mood()) || blank(request.companion())
                || blank(request.intensity()) || blank(request.ageRating())) {
            return ResponseEntity.badRequest().body(Map.of("message", REQUIRED_FIELDS_MESSAGE));
        }
        ChatbotResponse response = chatbot.recommend(request);
        return ResponseEntity.ok(response);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
