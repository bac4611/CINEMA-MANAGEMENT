package com.cinema.management.service.admin;

import com.cinema.management.model.dto.request.AdminSettingsRequest;
import com.cinema.management.model.dto.request.CreateStaffRequest;
import com.cinema.management.model.dto.request.UpdateStaffRequest;
import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.booking.Booking;
import com.cinema.management.model.entity.booking.Ticket;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.payment.Payment;
import com.cinema.management.model.entity.theater.Showtime;
import com.cinema.management.repository.account.UserRepository;
import com.cinema.management.repository.booking.TicketRepository;
import com.cinema.management.repository.movie.MovieRepository;
import com.cinema.management.repository.payment.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultAdminServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final MovieRepository movies = mock(MovieRepository.class);
    private DefaultAdminService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAdminService(users, payments, tickets, movies);
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void analyticsFiltersSummaryAndKeepsFullTimeline() {
        Movie movie = new Movie();
        movie.setMovieId(7);
        movie.setTitle("Cosmos");
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        Booking booking = new Booking();
        booking.setBookingId(11);
        booking.setStatus("Confirmed");
        Ticket ticket = new Ticket();
        ticket.setBooking(booking);
        ticket.setShowtime(showtime);
        booking.setTickets(List.of(ticket));
        Payment january = payment(booking, "2026-01-15T10:00:00", "100.00");
        Payment february = payment(booking, "2026-02-15T10:00:00", "200.00");
        booking.setPayments(List.of(january, february));
        when(payments.findAll()).thenReturn(List.of(january, february));
        when(tickets.findAll()).thenReturn(List.of(ticket));
        when(movies.findAll()).thenReturn(List.of(movie));

        var result = service.getAnalytics("2026-01", 7);

        assertEquals(new BigDecimal("100.00"), result.getTotalRevenue());
        assertEquals(1, result.getTotalTicketsSold());
        assertEquals(1, result.getConfirmedBookings());
        assertEquals(2, result.getRevenueByMonth().size());
        assertEquals("2026-01", result.getSelectedMonth());
    }

    @Test
    void staffPageClampsPageAndNeverReturnsPasswordHashes() {
        User staff = staffUser();
        when(users.findByRoleOrderByUserId(eq("Staff"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(staff), Pageable.ofSize(1), 6))
                .thenReturn(new PageImpl<>(List.of(staff), Pageable.ofSize(5), 6));

        var page = service.getStaffPage(99);

        assertEquals(2, page.currentPage());
        assertEquals(2, page.totalPages());
        assertEquals(6, page.totalCount());
        assertEquals("staff@example.com", page.staff().getFirst().email());
    }

    @Test
    void createAndUpdateStaffValidateAgeEmailAndPassword() {
        CreateStaffRequest create = new CreateStaffRequest();
        create.setFullName("Nguyen Van A");
        create.setEmail(" STAFF@EXAMPLE.COM ");
        create.setPhoneNumber("0901234567");
        create.setPassword("Password!1");
        create.setDateOfBirth(LocalDate.now().minusYears(15));
        assertEquals(AdminService.OperationStatus.INVALID_DATE_OF_BIRTH,
                service.createStaff(create).status());
        verify(users, never()).saveAndFlush(any());

        create.setDateOfBirth(LocalDate.of(2000, 1, 1));
        assertEquals(AdminService.OperationStatus.SUCCESS, service.createStaff(create).status());
        verify(users).saveAndFlush(argThat(user -> user.getRole().equals("Staff")
                && user.getEmail().equals("staff@example.com")
                && new BCryptPasswordEncoder().matches("Password!1", user.getPasswordHash())));

        User staff = staffUser();
        when(users.findByUserIdAndRole(5, "Staff")).thenReturn(Optional.of(staff));
        UpdateStaffRequest update = new UpdateStaffRequest();
        update.setUserId(5);
        update.setFullName("Updated Staff");
        update.setEmail("other@example.com");
        update.setPassword("");
        assertEquals(AdminService.OperationStatus.SUCCESS, service.updateStaff(update).status());
        assertTrue(new BCryptPasswordEncoder().matches("OldPassword!1", staff.getPasswordHash()));
    }

    @Test
    void settingsRequireCorrectCurrentPasswordBeforeChangingIt() {
        User admin = new User();
        admin.setUserId(1);
        admin.setRole("Admin");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(new BCryptPasswordEncoder().encode("OldPassword!1"));
        when(users.findByUserIdAndRole(1, "Admin")).thenReturn(Optional.of(admin));
        AdminSettingsRequest request = new AdminSettingsRequest();
        request.setFullName("Admin");
        request.setEmail("admin@example.com");
        request.setNewPassword("NewPassword!2");
        assertEquals(AdminService.OperationStatus.CURRENT_PASSWORD_REQUIRED,
                service.updateSettings(1, request).status());
        request.setCurrentPassword("wrong");
        assertEquals(AdminService.OperationStatus.INVALID_CURRENT_PASSWORD,
                service.updateSettings(1, request).status());
        request.setCurrentPassword("OldPassword!1");
        assertEquals(AdminService.OperationStatus.SUCCESS, service.updateSettings(1, request).status());
        assertTrue(new BCryptPasswordEncoder().matches("NewPassword!2", admin.getPasswordHash()));
    }

    private Payment payment(Booking booking, String date, String amount) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setStatus("Success");
        payment.setPaymentDate(LocalDateTime.parse(date));
        payment.setAmount(new BigDecimal(amount));
        return payment;
    }

    private User staffUser() {
        User staff = new User();
        staff.setUserId(5);
        staff.setFullName("Staff");
        staff.setEmail("staff@example.com");
        staff.setRole("Staff");
        staff.setPasswordHash(new BCryptPasswordEncoder().encode("OldPassword!1"));
        return staff;
    }
}
