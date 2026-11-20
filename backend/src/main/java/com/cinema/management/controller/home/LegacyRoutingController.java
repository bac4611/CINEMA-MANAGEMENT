package com.cinema.management.controller.home;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.URI;
import java.util.Map;

/** GET-only compatibility for bookmarks and previously sent email links. */
@Controller
public class LegacyRoutingController {
    static final Map<String, String> REDIRECTS = Map.ofEntries(
            Map.entry("/Account/Login", "/account/login"),
            Map.entry("/Account/login", "/account/login"),
            Map.entry("/account/Login", "/account/login"),
            Map.entry("/Account/AccessDenied", "/account/access-denied"),
            Map.entry("/Account/access-denied", "/account/access-denied"),
            Map.entry("/account/AccessDenied", "/account/access-denied"),
            Map.entry("/Account/Register", "/account/register"),
            Map.entry("/Account/register", "/account/register"),
            Map.entry("/account/Register", "/account/register"),
            Map.entry("/Account/EmailVerificationSent", "/account/email-verification-sent"),
            Map.entry("/Account/email-verification-sent", "/account/email-verification-sent"),
            Map.entry("/account/EmailVerificationSent", "/account/email-verification-sent"),
            Map.entry("/Account/ForgotPassword", "/account/forgot-password"),
            Map.entry("/Account/forgot-password", "/account/forgot-password"),
            Map.entry("/account/ForgotPassword", "/account/forgot-password"),
            Map.entry("/Account/ForgotPasswordConfirmation", "/account/forgot-password-confirmation"),
            Map.entry("/Account/forgot-password-confirmation", "/account/forgot-password-confirmation"),
            Map.entry("/account/ForgotPasswordConfirmation", "/account/forgot-password-confirmation"),
            Map.entry("/Account/ResetPassword", "/account/reset-password"),
            Map.entry("/Account/reset-password", "/account/reset-password"),
            Map.entry("/account/ResetPassword", "/account/reset-password"),
            Map.entry("/Account/ResetPasswordConfirmation", "/account/reset-password-confirmation"),
            Map.entry("/Account/reset-password-confirmation", "/account/reset-password-confirmation"),
            Map.entry("/account/ResetPasswordConfirmation", "/account/reset-password-confirmation"),
            Map.entry("/Account/ConfirmEmail", "/account/confirm-email"),
            Map.entry("/Account/confirm-email", "/account/confirm-email"),
            Map.entry("/account/ConfirmEmail", "/account/confirm-email"),
            Map.entry("/Account/Profile", "/account/profile"),
            Map.entry("/Account/profile", "/account/profile"),
            Map.entry("/account/Profile", "/account/profile"),
            Map.entry("/Admin", "/admin"),
            Map.entry("/Admin/", "/admin"),
            Map.entry("/Admin/Index", "/admin"),
            Map.entry("/Admin/index", "/admin"),
            Map.entry("/admin/", "/admin"),
            Map.entry("/admin/Index", "/admin"),
            Map.entry("/admin/index", "/admin"),
            Map.entry("/Admin/Analytics", "/admin/analytics"),
            Map.entry("/Admin/analytics", "/admin/analytics"),
            Map.entry("/admin/Analytics", "/admin/analytics"),
            Map.entry("/Admin/Staff", "/admin/staff"),
            Map.entry("/Admin/staff", "/admin/staff"),
            Map.entry("/admin/Staff", "/admin/staff"),
            Map.entry("/Admin/Settings", "/admin/settings"),
            Map.entry("/Admin/settings", "/admin/settings"),
            Map.entry("/admin/Settings", "/admin/settings"),
            Map.entry("/Booking", "/booking"),
            Map.entry("/Booking/", "/booking"),
            Map.entry("/Booking/Index", "/booking"),
            Map.entry("/Booking/index", "/booking"),
            Map.entry("/booking/", "/booking"),
            Map.entry("/booking/Index", "/booking"),
            Map.entry("/booking/index", "/booking"),
            Map.entry("/Booking/SelectShowtime", "/booking/select-showtime"),
            Map.entry("/Booking/select-showtime", "/booking/select-showtime"),
            Map.entry("/booking/SelectShowtime", "/booking/select-showtime"),
            Map.entry("/Booking/SelectShowtimeFromSchedule", "/booking/select-showtime-from-schedule"),
            Map.entry("/Booking/select-showtime-from-schedule", "/booking/select-showtime-from-schedule"),
            Map.entry("/booking/SelectShowtimeFromSchedule", "/booking/select-showtime-from-schedule"),
            Map.entry("/Booking/SelectSeats", "/booking/select-seats"),
            Map.entry("/Booking/select-seats", "/booking/select-seats"),
            Map.entry("/booking/SelectSeats", "/booking/select-seats"),
            Map.entry("/Booking/SelectConcessions", "/booking/select-concessions"),
            Map.entry("/Booking/select-concessions", "/booking/select-concessions"),
            Map.entry("/booking/SelectConcessions", "/booking/select-concessions"),
            Map.entry("/Booking/Checkout", "/booking/checkout"),
            Map.entry("/Booking/checkout", "/booking/checkout"),
            Map.entry("/booking/Checkout", "/booking/checkout"),
            Map.entry("/Booking/BookingExpired", "/booking/booking-expired"),
            Map.entry("/Booking/booking-expired", "/booking/booking-expired"),
            Map.entry("/booking/BookingExpired", "/booking/booking-expired"),
            Map.entry("/Home", "/home"),
            Map.entry("/Home/", "/home"),
            Map.entry("/Home/Index", "/home"),
            Map.entry("/Home/index", "/home"),
            Map.entry("/home/", "/home"),
            Map.entry("/home/Index", "/home"),
            Map.entry("/home/index", "/home"),
            Map.entry("/Home/Movie", "/movies"),
            Map.entry("/Home/movie", "/movies"),
            Map.entry("/home/Movie", "/movies"),
            Map.entry("/home/movie", "/movies"),
            Map.entry("/Home/Details", "/movies/details"),
            Map.entry("/Home/details", "/movies/details"),
            Map.entry("/home/Details", "/movies/details"),
            Map.entry("/home/details", "/movies/details"),
            Map.entry("/Home/TicketPricing", "/ticket-pricing"),
            Map.entry("/Home/ticket-pricing", "/ticket-pricing"),
            Map.entry("/home/TicketPricing", "/ticket-pricing"),
            Map.entry("/home/ticket-pricing", "/ticket-pricing"),
            Map.entry("/Staff", "/staff"),
            Map.entry("/Staff/", "/staff"),
            Map.entry("/Staff/Index", "/staff"),
            Map.entry("/Staff/index", "/staff"),
            Map.entry("/staff/", "/staff"),
            Map.entry("/staff/Index", "/staff"),
            Map.entry("/staff/index", "/staff"),
            Map.entry("/Staff/GetSchedule", "/staff/schedule"),
            Map.entry("/Staff/schedule", "/staff/schedule"),
            Map.entry("/staff/GetSchedule", "/staff/schedule"),
            Map.entry("/Staff/GetScreeningRoomState", "/staff/screening-room-state"),
            Map.entry("/Staff/screening-room-state", "/staff/screening-room-state"),
            Map.entry("/staff/GetScreeningRoomState", "/staff/screening-room-state"),
            Map.entry("/Staff/Halls", "/staff/halls"),
            Map.entry("/Staff/halls", "/staff/halls"),
            Map.entry("/staff/Halls", "/staff/halls"),
            Map.entry("/Staff/Concessions", "/staff/concessions"),
            Map.entry("/Staff/concessions", "/staff/concessions"),
            Map.entry("/staff/Concessions", "/staff/concessions"),
            Map.entry("/Staff/CreateConcession", "/staff/create-concession"),
            Map.entry("/Staff/create-concession", "/staff/create-concession"),
            Map.entry("/staff/CreateConcession", "/staff/create-concession"),
            Map.entry("/Staff/EditConcession", "/staff/edit-concession"),
            Map.entry("/Staff/edit-concession", "/staff/edit-concession"),
            Map.entry("/staff/EditConcession", "/staff/edit-concession"));

    @GetMapping({"/Account/Login", "/Account/login", "/account/Login", "/Account/AccessDenied", "/Account/access-denied", "/account/AccessDenied", "/Account/Register", "/Account/register", "/account/Register", "/Account/EmailVerificationSent", "/Account/email-verification-sent", "/account/EmailVerificationSent", "/Account/ForgotPassword", "/Account/forgot-password", "/account/ForgotPassword", "/Account/ForgotPasswordConfirmation", "/Account/forgot-password-confirmation", "/account/ForgotPasswordConfirmation", "/Account/ResetPassword", "/Account/reset-password", "/account/ResetPassword", "/Account/ResetPasswordConfirmation", "/Account/reset-password-confirmation", "/account/ResetPasswordConfirmation", "/Account/ConfirmEmail", "/Account/confirm-email", "/account/ConfirmEmail", "/Account/Profile", "/Account/profile", "/account/Profile", "/Admin", "/Admin/", "/Admin/Index", "/Admin/index", "/admin/", "/admin/Index", "/admin/index", "/Admin/Analytics", "/Admin/analytics", "/admin/Analytics", "/Admin/Staff", "/Admin/staff", "/admin/Staff", "/Admin/Settings", "/Admin/settings", "/admin/Settings", "/Booking", "/Booking/", "/Booking/Index", "/Booking/index", "/booking/", "/booking/Index", "/booking/index", "/Booking/SelectShowtime", "/Booking/select-showtime", "/booking/SelectShowtime", "/Booking/SelectShowtimeFromSchedule", "/Booking/select-showtime-from-schedule", "/booking/SelectShowtimeFromSchedule", "/Booking/SelectSeats", "/Booking/select-seats", "/booking/SelectSeats", "/Booking/SelectConcessions", "/Booking/select-concessions", "/booking/SelectConcessions", "/Booking/Checkout", "/Booking/checkout", "/booking/Checkout", "/Booking/BookingExpired", "/Booking/booking-expired", "/booking/BookingExpired", "/Home", "/Home/", "/Home/Index", "/Home/index", "/home/", "/home/Index", "/home/index", "/Home/Movie", "/Home/movie", "/home/Movie", "/home/movie", "/Home/Details", "/Home/details", "/home/Details", "/home/details", "/Home/TicketPricing", "/Home/ticket-pricing", "/home/TicketPricing", "/home/ticket-pricing", "/Staff", "/Staff/", "/Staff/Index", "/Staff/index", "/staff/", "/staff/Index", "/staff/index", "/Staff/GetSchedule", "/Staff/schedule", "/staff/GetSchedule", "/Staff/GetScreeningRoomState", "/Staff/screening-room-state", "/staff/GetScreeningRoomState", "/Staff/Halls", "/Staff/halls", "/staff/Halls", "/Staff/Concessions", "/Staff/concessions", "/staff/Concessions", "/Staff/CreateConcession", "/Staff/create-concession", "/staff/CreateConcession", "/Staff/EditConcession", "/Staff/edit-concession", "/staff/EditConcession"})
    public ResponseEntity<Void> redirect(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return redirectTo(REDIRECTS.get(path), request);
    }

    @GetMapping({"/Home/Details/{id}", "/Home/details/{id}", "/home/Details/{id}", "/home/details/{id}"})
    public ResponseEntity<Void> movieDetails(@PathVariable int id, HttpServletRequest request) {
        return redirectTo("/movies/details/" + id, request);
    }

    private ResponseEntity<Void> redirectTo(String target, HttpServletRequest request) {
        String query = request.getQueryString();
        String location = request.getContextPath() + target + (query == null ? "" : "?" + query);
        return ResponseEntity.status(308).location(URI.create(location)).build();
    }
}
