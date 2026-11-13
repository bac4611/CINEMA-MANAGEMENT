package com.cinema.management.controller.booking;

import com.cinema.management.model.dto.request.SePayWebhookRequest;
import com.cinema.management.service.booking.BookingService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class SePayWebhookController {
    private final BookingService bookings;
    private final Environment environment;
    private final ObjectMapper json;

    public SePayWebhookController(BookingService bookings, Environment environment, ObjectMapper json) {
        this.bookings = bookings;
        this.environment = environment;
        this.json = json;
    }

    @PostMapping("/webhooks/sepay")
    public ResponseEntity<?> sePay(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @RequestBody(required = false) SePayWebhookRequest request) {
        String apiKey = environment.getProperty("sepay.webhook-api-key");
        if (apiKey != null && !apiKey.isBlank() && !("Apikey " + apiKey).equals(authorization)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }
        String rawPayload;
        try { rawPayload = request == null ? null : json.writeValueAsString(request); }
        catch (JacksonException exception) { rawPayload = null; }
        var result = bookings.processSePayWebhook(request, rawPayload);
        if (result.status() == BookingService.Status.SUCCESS) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false,
                "message", result.message() == null ? "Payment failed" : result.message()));
    }
}
