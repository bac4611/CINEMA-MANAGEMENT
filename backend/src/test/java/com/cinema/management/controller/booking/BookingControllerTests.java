package com.cinema.management.controller.booking;

import com.cinema.management.model.dto.request.SePayWebhookRequest;
import com.cinema.management.service.booking.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingControllerTests {
    private final BookingService bookings = mock(BookingService.class);
    private final Environment environment = mock(Environment.class);

    @Test
    void selectingShowtimeWithoutSessionUserRedirectsBeforeCallingService() {
        BookingController controller = new BookingController(bookings, environment);

        String view = controller.selectShowtime(7, new MockHttpServletRequest(),
                new org.springframework.ui.ConcurrentModel(), new RedirectAttributesModelMap());

        assertEquals("redirect:/Account/Login", view);
        verifyNoInteractions(bookings);
    }

    @Test
    void seatConflictIsTranslatedToHttp409ByController() {
        BookingController controller = new BookingController(bookings, environment);
        when(bookings.updateSeat(null, 12, true)).thenReturn(
                new BookingService.SeatHoldResult(BookingService.Status.CONFLICT, false, "Ghế đã được giữ."));

        var response = controller.holdSeat(12, true, new MockHttpServletRequest());

        assertEquals(409, response.getStatusCode().value());
        verify(bookings).updateSeat(null, 12, true);
    }

    @Test
    void webhookChecksApiKeyBeforeDelegatingPaymentLogic() {
        when(environment.getProperty("sepay.webhook-api-key")).thenReturn("secret");
        SePayWebhookController controller = new SePayWebhookController(bookings, environment, new ObjectMapper());

        var response = controller.sePay("Apikey wrong", new SePayWebhookRequest());

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(bookings);
    }

    @Test
    void validWebhookDelegatesToServiceAndReturnsSuccess() {
        when(environment.getProperty("sepay.webhook-api-key")).thenReturn("secret");
        when(bookings.processSePayWebhook(any(SePayWebhookRequest.class), anyString())).thenReturn(
                new BookingService.WebhookResult(BookingService.Status.SUCCESS, null));
        SePayWebhookController controller = new SePayWebhookController(bookings, environment, new ObjectMapper());

        var response = controller.sePay("Apikey secret", new SePayWebhookRequest());

        assertEquals(200, response.getStatusCode().value());
        verify(bookings).processSePayWebhook(any(SePayWebhookRequest.class), anyString());
    }
}
