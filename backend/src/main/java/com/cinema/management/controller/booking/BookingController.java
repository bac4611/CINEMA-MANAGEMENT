package com.cinema.management.controller.booking;

import com.cinema.management.model.dto.request.SePayWebhookRequest;
import com.cinema.management.model.dto.request.SelectConcessionsRequest;
import com.cinema.management.model.dto.request.SelectShowtimeRequest;
import com.cinema.management.service.booking.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

@Controller
public class BookingController {
    private static final String HOLD_ID = "BookingHoldId";
    private static final List<String> BOOKING_SESSION_KEYS = List.of(
            HOLD_ID, "SelectedMovieId", "SelectedShowtimeId", "SelectedDate", "SelectedTime",
            "SelectedFormat", "SelectedOfferCode", "SelectedSeats", "SelectedConcessions",
            "PaymentReference", "PaymentExpiresAtUtc");
    private final BookingService bookings;
    private final Environment environment;
    public BookingController(BookingService bookings, Environment environment) {
        this.bookings = bookings;
        this.environment = environment;
    }

    @GetMapping("/booking")
    public String index(@RequestParam(required = false) Integer movieId,
                        @RequestParam(required = false) LocalDate date, Model model, HttpServletRequest request) {
        var schedule = bookings.getSchedule(movieId, date);
        if (schedule == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // The schedule can exceed the servlet buffer before its POST form is
        // rendered. Persist the deferred CSRF token before response headers commit.
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrf != null) csrf.getToken();
        model.addAttribute("booking", schedule);
        return "booking/index";
    }

    @GetMapping("/booking/select-showtime")
    public String selectShowtime(@RequestParam int movieId, HttpServletRequest request,
                                 Model model, RedirectAttributes flash) {
        Integer userId = currentUserId(request);
        if (userId == null) return requireLogin(flash);
        var result = bookings.prepareShowtime(userId, movieId, currentHoldId(request));
        if (result.status() == BookingService.Status.NOT_FOUND) throw notFound();
        if (result.status() == BookingService.Status.PAYMENT_PENDING) {
            request.getSession().setAttribute("PaymentReference", result.paymentReference());
            return "redirect:/booking/payment";
        }
        request.getSession().setAttribute(HOLD_ID, result.holdId().toString());
        model.addAttribute("booking", result.model());
        return "booking/select-showtime";
    }

    @PostMapping({"/booking/select-showtime", "/Booking/SelectShowtime", "/Booking/select-showtime", "/booking/SelectShowtime"})
    public String selectShowtime(@ModelAttribute SelectShowtimeRequest form, HttpServletRequest request,
                                 RedirectAttributes flash) {
        return selectShowtimeInternal(form, request, flash, false);
    }

    @PostMapping({"/booking/select-showtime-from-schedule", "/Booking/SelectShowtimeFromSchedule", "/Booking/select-showtime-from-schedule", "/booking/SelectShowtimeFromSchedule"})
    public String selectShowtimeFromSchedule(@ModelAttribute SelectShowtimeRequest form,
                                             HttpServletRequest request, RedirectAttributes flash) {
        return selectShowtimeInternal(form, request, flash, true);
    }

    @GetMapping("/booking/select-showtime-from-schedule")
    public String selectShowtimeFromScheduleGet() {
        return "redirect:/booking";
    }

    @GetMapping("/booking/select-seats")
    public String selectSeats(HttpServletRequest request, Model model, RedirectAttributes flash) {
        UUID holdId = currentHoldId(request);
        var booking = bookings.getSeatSelectionView(holdId).orElse(null);
        if (booking == null) {
            flash.addFlashAttribute("alertError", "Phiên giữ chỗ không tồn tại hoặc đã hết hạn.");
            return "redirect:/booking";
        }
        model.addAttribute("booking", booking);
        var seatRows = new TreeMap<String, List<com.cinema.management.model.viewmodel.booking.SeatChoiceViewModel>>();
        booking.getSeatChoices().stream()
                .sorted(Comparator.comparing(com.cinema.management.model.viewmodel.booking.SeatChoiceViewModel::getSeatCode))
                .forEach(seat -> seatRows.computeIfAbsent(seat.getSeatCode().substring(0, 1), key -> new ArrayList<>()).add(seat));
        model.addAttribute("seatRows", seatRows);
        return "booking/select-seats";
    }

    @PostMapping({"/booking/select-seats", "/Booking/SelectSeats", "/Booking/select-seats", "/booking/SelectSeats"})
    public String selectSeatsPost(HttpServletRequest request, RedirectAttributes flash) {
        var result = bookings.validateSelectedSeats(currentHoldId(request));
        if (result.status() == BookingService.Status.EXPIRED) {
            flash.addFlashAttribute("alertError", result.message());
            return "redirect:/booking";
        }
        if (result.status() != BookingService.Status.SUCCESS) {
            flash.addFlashAttribute("alertError", result.message());
            return "redirect:/booking/select-seats";
        }
        return "redirect:/booking/select-concessions";
    }

    @PostMapping({"/booking/hold-seat", "/Booking/HoldSeat", "/Booking/hold-seat", "/booking/HoldSeat"})
    @ResponseBody
    public ResponseEntity<?> holdSeat(@RequestParam int seatId, @RequestParam boolean select,
                                      HttpServletRequest request) {
        var result = bookings.updateSeat(currentHoldId(request), seatId, select);
        if (result.status() == BookingService.Status.SUCCESS) {
            return ResponseEntity.ok(Map.of("selected", result.selected()));
        }
        return response(result.status(), result.message());
    }

    @PostMapping({"/booking/update-combo-hold", "/Booking/UpdateComboHold", "/Booking/update-combo-hold", "/booking/UpdateComboHold"})
    @ResponseBody
    public ResponseEntity<?> updateComboHold(@RequestParam int comboId, @RequestParam int change,
                                             HttpServletRequest request) {
        var result = bookings.updateCombo(currentHoldId(request), comboId, change);
        if (result.status() == BookingService.Status.SUCCESS) {
            return ResponseEntity.ok(Map.of("quantity", result.quantity(), "stockQuantity", result.stockQuantity()));
        }
        return response(result.status(), result.message());
    }

    @PostMapping({"/booking/clear-combo-holds", "/Booking/ClearComboHolds", "/Booking/clear-combo-holds", "/booking/ClearComboHolds"})
    @ResponseBody
    public ResponseEntity<?> clearComboHolds(HttpServletRequest request) {
        var result = bookings.clearCombos(currentHoldId(request));
        return result.status() == BookingService.Status.SUCCESS
                ? ResponseEntity.ok(Map.of("cleared", true)) : response(result.status(), result.message());
    }

    @GetMapping("/booking/select-concessions")
    public String selectConcessions(HttpServletRequest request, Model model, RedirectAttributes flash) {
        var booking = bookings.getBookingView(currentHoldId(request), true).orElse(null);
        if (booking == null) {
            flash.addFlashAttribute("alertError", "Phiên giữ chỗ đã hết hạn hoặc chưa chọn ghế.");
            return "redirect:/booking/select-seats";
        }
        model.addAttribute("booking", booking);
        return "booking/select-concessions";
    }

    @PostMapping({"/booking/select-concessions", "/Booking/SelectConcessions", "/Booking/select-concessions", "/booking/SelectConcessions"})
    public String selectConcessions(@ModelAttribute SelectConcessionsRequest ignored,
                                    HttpServletRequest request, RedirectAttributes flash) {
        var result = bookings.validateSelectedSeats(currentHoldId(request));
        if (result.status() != BookingService.Status.SUCCESS) {
            flash.addFlashAttribute("alertError", result.message());
            return result.status() == BookingService.Status.EXPIRED
                    ? "redirect:/booking" : "redirect:/booking/select-seats";
        }
        return "redirect:/booking/checkout";
    }

    @GetMapping("/booking/checkout")
    public String checkout(HttpServletRequest request, Model model, RedirectAttributes flash) {
        var booking = bookings.getBookingView(currentHoldId(request), true).orElse(null);
        if (booking == null) {
            flash.addFlashAttribute("alertError", "Phiên giữ chỗ đã hết hạn hoặc chưa chọn ghế.");
            return "redirect:/booking";
        }
        model.addAttribute("booking", booking);
        return "booking/checkout";
    }

    @PostMapping({"/booking/start-payment", "/Booking/StartPayment", "/Booking/start-payment", "/booking/StartPayment"})
    public String startPayment(HttpServletRequest request, RedirectAttributes flash) {
        Integer userId = currentUserId(request);
        if (userId == null) return "redirect:/account/login";
        var result = bookings.startPayment(userId, currentHoldId(request));
        if (result.status() != BookingService.Status.SUCCESS) {
            flash.addFlashAttribute("paymentError", result.message());
            return result.status() == BookingService.Status.EXPIRED
                    ? "redirect:/booking" : "redirect:/booking/checkout";
        }
        request.getSession().setAttribute("PaymentReference", result.paymentReference());
        request.getSession().setAttribute("PaymentExpiresAtUtc", result.expiresAt().toString());
        return "redirect:/booking/payment";
    }

    @GetMapping({"/booking/payment", "/Booking/Payment", "/Booking/payment", "/booking/Payment"})
    public String payment(HttpServletRequest request, Model model, RedirectAttributes flash) {
        Integer userId = currentUserId(request);
        if (userId == null) return "redirect:/account/login";
        String reference = sessionString(request, "PaymentReference");
        if (!hasText(reference)) {
            reference = bookings.findPendingPaymentReference(currentHoldId(request), userId).orElse(null);
        }
        if (!hasText(reference)) return "redirect:/booking/checkout";
        var result = bookings.getPaymentPage(userId, reference);
        if (result.bookingId() != null) {
            clearBookingSession(request);
            return "redirect:/booking/payment-success?bookingId=" + result.bookingId();
        }
        if (result.status() != BookingService.Status.SUCCESS) {
            clearBookingSession(request);
            flash.addFlashAttribute("paymentError", result.message());
            return "redirect:/booking";
        }
        model.addAttribute("payment", result.model());
        return "booking/payment";
    }

    @PostMapping({"/booking/complete-dev-payment", "/Booking/CompleteDevPayment", "/Booking/complete-dev-payment", "/booking/CompleteDevPayment"})
    public String completeDevPayment(@RequestParam String paymentReference,
                                     HttpServletRequest request, RedirectAttributes flash) {
        Integer userId = currentUserId(request);
        if (userId == null) return "redirect:/account/login";
        if (!isDevelopment()) throw notFound();
        if (!Objects.equals(paymentReference, sessionString(request, "PaymentReference"))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mã thanh toán không hợp lệ.");
        }
        var result = bookings.completeDevelopmentPayment(userId, paymentReference);
        if (result.status() != BookingService.Status.SUCCESS || result.bookingId() == null) {
            flash.addFlashAttribute("paymentError", result.message());
            return "redirect:/booking/payment";
        }
        clearBookingSession(request);
        flash.addFlashAttribute("paymentSuccess", "Thanh toán thành công.");
        return "redirect:/booking/payment-success?bookingId=" + result.bookingId();
    }

    @GetMapping({"/booking/payment-status", "/Booking/PaymentStatus", "/Booking/payment-status", "/booking/PaymentStatus"})
    @ResponseBody
    public ResponseEntity<?> paymentStatus(@RequestParam String paymentReference, HttpServletRequest request) {
        Integer userId = currentUserId(request);
        if (userId == null || !hasText(paymentReference)) {
            return ResponseEntity.badRequest().body(Map.of("status", "Invalid"));
        }
        var result = bookings.getPaymentStatus(userId, paymentReference);
        if (result.status() == BookingService.Status.NOT_FOUND) {
            return ResponseEntity.status(404).body(Map.of("status", "NotFound"));
        }
        String status = switch (result.status()) {
            case SUCCESS -> "Success";
            case EXPIRED -> "Expired";
            default -> "Pending";
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("bookingId", result.bookingId());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/booking/booking-expired")
    public String bookingExpired(HttpServletRequest request, RedirectAttributes flash) {
        UUID holdId = currentHoldId(request);
        var state = bookings.getHoldState(holdId);
        if (state.status() == BookingService.Status.PAYMENT_PENDING) return "redirect:/booking/payment";
        if (state.status() == BookingService.Status.SUCCESS) {
            return state.showtimeId() == null
                    ? "redirect:/booking/select-showtime?movieId=" + state.movieId()
                    : "redirect:/booking/select-seats";
        }
        bookings.expireHold(holdId);
        clearBookingSession(request);
        flash.addFlashAttribute("alertError", "Phiên giữ chỗ 10 phút đã hết hạn. Ghế và combo đã được hoàn lại.");
        return "redirect:/booking";
    }

    @PostMapping({"/booking/cancel-booking", "/Booking/CancelBooking", "/Booking/cancel-booking", "/booking/CancelBooking"})
    public String cancelBooking(HttpServletRequest request, RedirectAttributes flash) {
        bookings.cancelHold(currentHoldId(request));
        clearBookingSession(request);
        flash.addFlashAttribute("bookingMessage", "Đã hủy quá trình đặt vé và hoàn lại ghế/combo.");
        return "redirect:/home";
    }

    @GetMapping({"/booking/payment-success", "/Booking/PaymentSuccess", "/Booking/payment-success", "/booking/PaymentSuccess"})
    public String paymentSuccess(@RequestParam int bookingId, HttpServletRequest request, Model model) {
        Integer userId = currentUserId(request);
        if (userId == null) return "redirect:/account/login";
        var booking = bookings.getCompletedBooking(userId, bookingId).orElseThrow(this::notFound);
        model.addAttribute("booking", booking);
        return "booking/payment-success";
    }

    private String selectShowtimeInternal(SelectShowtimeRequest form, HttpServletRequest request,
                                          RedirectAttributes flash, boolean requireFuture) {
        Integer userId = currentUserId(request);
        if (userId == null) return requireLogin(flash);
        var result = bookings.selectShowtime(userId, currentHoldId(request), form, requireFuture);
        if (result.status() == BookingService.Status.PAYMENT_PENDING) {
            request.getSession().setAttribute("PaymentReference", result.paymentReference());
            return "redirect:/booking/payment";
        }
        if (result.status() != BookingService.Status.SUCCESS) {
            flash.addFlashAttribute("alertError", result.message());
            return requireFuture ? "redirect:/booking"
                    : "redirect:/booking/select-showtime?movieId=" + form.getMovieId();
        }
        request.getSession().setAttribute(HOLD_ID, result.holdId().toString());
        request.getSession().setAttribute("SelectedShowtimeId", result.showtimeId());
        request.getSession().setAttribute("SelectedMovieId", result.movieId());
        request.getSession().setAttribute("SelectedFormat", result.format());
        request.getSession().setAttribute("SelectedDate", result.date());
        request.getSession().setAttribute("SelectedTime", result.time());
        if (hasText(form.getOfferCode())) request.getSession().setAttribute("SelectedOfferCode", form.getOfferCode().trim().toUpperCase());
        else request.getSession().removeAttribute("SelectedOfferCode");
        request.getSession().removeAttribute("SelectedSeats");
        request.getSession().removeAttribute("SelectedConcessions");
        return "redirect:/booking/select-seats";
    }

    private String requireLogin(RedirectAttributes flash) {
        flash.addFlashAttribute("alertError", "Vui lòng đăng nhập hoặc đăng ký tài khoản trước khi đặt vé.");
        return "redirect:/account/login";
    }

    private ResponseEntity<?> response(BookingService.Status status, String message) {
        int code = switch (status) {
            case NOT_FOUND -> 404;
            case CONFLICT, EXPIRED -> 409;
            case UNAUTHORIZED -> 401;
            default -> 400;
        };
        return ResponseEntity.status(code).body(Map.of("message", message == null ? "Yêu cầu không hợp lệ." : message));
    }

    private Integer currentUserId(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && session.getAttribute("UserID") instanceof Integer id ? id : null;
    }

    private UUID currentHoldId(HttpServletRequest request) {
        String value = sessionString(request, HOLD_ID);
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private String sessionString(HttpServletRequest request, String key) {
        var session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(key);
        return value == null ? null : value.toString();
    }

    private void clearBookingSession(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) BOOKING_SESSION_KEYS.forEach(session::removeAttribute);
    }

    private boolean isDevelopment() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("dev") || profile.equalsIgnoreCase("development"));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private org.springframework.web.server.ResponseStatusException notFound() {
        return new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
