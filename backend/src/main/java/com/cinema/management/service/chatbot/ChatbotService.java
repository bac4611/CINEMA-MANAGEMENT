package com.cinema.management.service.chatbot;

import com.cinema.management.model.dto.request.GenreRecommendationInput;
import com.cinema.management.model.dto.response.ChatbotResponse;

public interface ChatbotService {
    ChatbotResponse recommend(GenreRecommendationInput input);
}
