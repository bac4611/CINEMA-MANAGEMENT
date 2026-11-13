package com.cinema.management.service.booking;

import com.cinema.management.model.dto.request.SePayWebhookRequest;
import com.cinema.management.model.dto.request.SelectShowtimeRequest;
import com.cinema.management.model.entity.booking.Booking;
import com.cinema.management.model.viewmodel.booking.BookingSchedulePageViewModel;
import com.cinema.management.model.viewmodel.booking.BookingViewModel;
import com.cinema.management.model.viewmodel.booking.PaymentPageViewModel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BookingService {
    BookingSchedulePageViewModel getSchedule(Integer movieId, LocalDate date);
    ShowtimePageResult prepareShowtime(int userId, int movieId, UUID currentHoldId);
    ShowtimeSelectionResult selectShowtime(int userId, UUID currentHoldId,
                                           SelectShowtimeRequest request, boolean requireFutureShowtime);
    Optional<BookingViewModel> getBookingView(UUID holdId, boolean requireSeats);
    ValidationResult validateSelectedSeats(UUID holdId);
    SeatHoldResult updateSeat(UUID holdId, int seatId, boolean select);
    ComboHoldResult updateCombo(UUID holdId, int comboId, int change);
    ValidationResult clearCombos(UUID holdId);
    PaymentIntentResult startPayment(int userId, UUID holdId);
    PaymentViewResult getPaymentPage(int userId, String paymentReference);
    PaymentCompletionResult completeDevelopmentPayment(int userId, String paymentReference);
    WebhookResult processSePayWebhook(SePayWebhookRequest request, String rawPayload);
    PaymentStatusResult getPaymentStatus(int userId, String paymentReference);
    HoldState getHoldState(UUID holdId);
    void expireHold(UUID holdId);
    void cancelHold(UUID holdId);
    Optional<Booking> getCompletedBooking(int userId, int bookingId);
    Optional<String> findPendingPaymentReference(UUID holdId, Integer userId);

    enum Status { SUCCESS, INVALID, NOT_FOUND, EXPIRED, CONFLICT, UNAUTHORIZED, PAYMENT_PENDING }
    record ValidationResult(Status status, String message) { }
    record ShowtimePageResult(Status status, BookingViewModel model, UUID holdId,
                              String paymentReference, String message) { }
    record ShowtimeSelectionResult(Status status, UUID holdId, int movieId, int showtimeId,
                                   String format, String date, String time, String paymentReference,
                                   String message) { }
    record SeatHoldResult(Status status, boolean selected, String message) { }
    record ComboHoldResult(Status status, int quantity, int stockQuantity, String message) { }
    record PaymentIntentResult(Status status, String paymentReference, Instant expiresAt, String message) { }
    record PaymentViewResult(Status status, PaymentPageViewModel model, Integer bookingId, String message) { }
    record PaymentCompletionResult(Status status, Integer bookingId, String message) { }
    record WebhookResult(Status status, String message) { }
    record PaymentStatusResult(Status status, Integer bookingId) { }
    record HoldState(Status status, Integer movieId, Integer showtimeId) { }
}
