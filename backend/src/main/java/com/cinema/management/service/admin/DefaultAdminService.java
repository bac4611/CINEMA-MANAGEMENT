package com.cinema.management.service.admin;

import com.cinema.management.model.dto.request.AdminSettingsRequest;
import com.cinema.management.model.dto.request.CreateStaffRequest;
import com.cinema.management.model.dto.request.UpdateStaffRequest;
import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.booking.Ticket;
import com.cinema.management.model.entity.payment.Payment;
import com.cinema.management.model.viewmodel.admin.*;
import com.cinema.management.repository.account.UserRepository;
import com.cinema.management.repository.booking.TicketRepository;
import com.cinema.management.repository.movie.MovieRepository;
import com.cinema.management.repository.payment.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DefaultAdminService implements AdminService {
    private static final int PAGE_SIZE = 5;
    private static final LocalDate EARLIEST_DATE_OF_BIRTH = LocalDate.of(1930, 1, 1);
    private final UserRepository users;
    private final PaymentRepository payments;
    private final TicketRepository tickets;
    private final MovieRepository movies;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public DefaultAdminService(UserRepository users, PaymentRepository payments,
                               TicketRepository tickets, MovieRepository movies) {
        this.users = users;
        this.payments = payments;
        this.tickets = tickets;
        this.movies = movies;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAnalyticsViewModel getAnalytics(String month, Integer movieId) {
        YearMonth selectedMonth = parseMonth(month);
        LocalDateTime from = selectedMonth == null ? null : selectedMonth.atDay(1).atStartOfDay();
        LocalDateTime to = selectedMonth == null ? null : selectedMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<Payment> successfulPayments = payments.findAll().stream()
                .filter(payment -> "Success".equals(payment.getStatus()))
                .filter(payment -> payment.getBooking() != null
                        && "Confirmed".equals(payment.getBooking().getStatus()))
                .toList();

        List<Payment> filteredPayments = successfulPayments.stream()
                .filter(payment -> isWithin(payment.getPaymentDate(), from, to))
                .filter(payment -> movieId == null || bookingContainsMovie(payment, movieId))
                .toList();

        List<Ticket> paidTickets = tickets.findAll().stream()
                .filter(ticket -> ticket.getBooking() != null
                        && "Confirmed".equals(ticket.getBooking().getStatus()))
                .filter(ticket -> firstSuccessfulPaymentDate(ticket) != null)
                .filter(ticket -> isWithin(firstSuccessfulPaymentDate(ticket), from, to))
                .filter(ticket -> movieId == null || ticketMovieId(ticket) == movieId)
                .toList();

        Map<YearMonth, BigDecimal> timeline = successfulPayments.stream()
                .filter(payment -> movieId == null || bookingContainsMovie(payment, movieId))
                .collect(Collectors.groupingBy(
                        payment -> YearMonth.from(payment.getPaymentDate()),
                        TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));

        List<MonthlyRevenueItem> revenueByMonth = timeline.entrySet().stream().map(entry -> {
            MonthlyRevenueItem item = new MonthlyRevenueItem();
            item.setYear(entry.getKey().getYear());
            item.setMonth(entry.getKey().getMonthValue());
            item.setLabel("%02d/%d".formatted(entry.getKey().getMonthValue(), entry.getKey().getYear()));
            item.setRevenue(entry.getValue());
            return item;
        }).toList();

        List<AnalyticsMonthOption> monthOptions = successfulPayments.stream()
                .map(payment -> YearMonth.from(payment.getPaymentDate()))
                .distinct().sorted(Comparator.reverseOrder()).map(value -> {
                    AnalyticsMonthOption option = new AnalyticsMonthOption();
                    option.setValue(value.format(DateTimeFormatter.ofPattern("yyyy-MM")));
                    option.setText("Tháng %02d/%d".formatted(value.getMonthValue(), value.getYear()));
                    return option;
                }).toList();

        List<AnalyticsMovieOption> movieOptions = movies.findAll().stream()
                .sorted(Comparator.comparing(movie -> movie.getTitle().toLowerCase(Locale.ROOT)))
                .map(movie -> {
                    AnalyticsMovieOption option = new AnalyticsMovieOption();
                    option.setMovieId(movie.getMovieId());
                    option.setTitle(movie.getTitle());
                    return option;
                }).toList();

        AdminAnalyticsViewModel result = new AdminAnalyticsViewModel();
        result.setSelectedMonth(selectedMonth == null ? null : selectedMonth.toString());
        result.setSelectedMovieId(movieId);
        result.setTotalRevenue(filteredPayments.stream()
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setTotalTicketsSold(paidTickets.size());
        result.setConfirmedBookings((int) filteredPayments.stream()
                .map(payment -> payment.getBooking().getBookingId()).distinct().count());
        result.setMonths(monthOptions);
        result.setMovies(movieOptions);
        result.setRevenueByMonth(revenueByMonth);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public StaffPageViewModel getStaffPage(int requestedPage) {
        long totalCount = users.findByRoleOrderByUserId("Staff", PageRequest.of(0, 1)).getTotalElements();
        int totalPages = (int) Math.ceil(totalCount / (double) PAGE_SIZE);
        int page = Math.max(requestedPage, 1);
        if (totalPages > 0) {
            page = Math.min(page, totalPages);
        }
        var result = users.findByRoleOrderByUserId("Staff", PageRequest.of(page - 1, PAGE_SIZE));
        var staff = result.getContent().stream()
                .map(user -> new StaffPageViewModel.StaffItem(user.getUserId(), user.getFullName(),
                        user.getEmail(), user.getPhoneNumber(), user.getDateOfBirth(), user.isStatus()))
                .toList();
        return new StaffPageViewModel(staff, totalCount, page, totalPages, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminSettingsViewModel> getSettings(int adminId) {
        return users.findByUserIdAndRole(adminId, "Admin")
                .map(user -> new AdminSettingsViewModel(user.getUserId(), user.getFullName(), user.getEmail()));
    }

    @Override
    public OperationResult updateSettings(int adminId, AdminSettingsRequest request) {
        User admin = users.findByUserIdAndRole(adminId, "Admin").orElse(null);
        if (admin == null) {
            return new OperationResult(OperationStatus.NOT_FOUND, "Không tìm thấy tài khoản Admin.");
        }
        String email = normalizeEmail(request.getEmail());
        if (users.existsByEmailIgnoreCaseAndUserIdNot(email, adminId)) {
            return new OperationResult(OperationStatus.DUPLICATE_EMAIL, "Email này đã tồn tại.");
        }
        if (hasText(request.getNewPassword())) {
            if (!hasText(request.getCurrentPassword())) {
                return new OperationResult(OperationStatus.CURRENT_PASSWORD_REQUIRED, "Vui lòng nhập mật khẩu cũ.");
            }
            if (!isPasswordValid(request.getCurrentPassword(), admin.getPasswordHash())) {
                return new OperationResult(OperationStatus.INVALID_CURRENT_PASSWORD, "Mật khẩu cũ không đúng.");
            }
            admin.setPasswordHash(passwords.encode(request.getNewPassword()));
        }
        admin.setFullName(request.getFullName().trim());
        admin.setEmail(email);
        users.saveAndFlush(admin);
        return OperationResult.success("Cập nhật tài khoản thành công.");
    }

    @Override
    public OperationResult createStaff(CreateStaffRequest request) {
        if (!isValidDateOfBirth(request.getDateOfBirth())) {
            return invalidDateOfBirth();
        }
        String email = normalizeEmail(request.getEmail());
        if (users.existsByEmailIgnoreCase(email)) {
            return duplicateEmail();
        }
        User staff = new User();
        copyStaffFields(staff, request.getFullName(), email, request.getPhoneNumber(), request.getDateOfBirth());
        staff.setPasswordHash(passwords.encode(request.getPassword()));
        staff.setStatus(true);
        staff.setRole("Staff");
        try {
            users.saveAndFlush(staff);
            return OperationResult.success("Tạo tài khoản thành công.");
        } catch (DataIntegrityViolationException exception) {
            return duplicateEmail();
        }
    }

    @Override
    public OperationResult updateStaff(UpdateStaffRequest request) {
        if (!isValidDateOfBirth(request.getDateOfBirth())) {
            return invalidDateOfBirth();
        }
        User staff = users.findByUserIdAndRole(request.getUserId(), "Staff").orElse(null);
        if (staff == null) {
            return new OperationResult(OperationStatus.NOT_FOUND, "Không tìm thấy tài khoản nhân viên.");
        }
        String email = normalizeEmail(request.getEmail());
        if (users.existsByEmailIgnoreCaseAndUserIdNot(email, staff.getUserId())) {
            return duplicateEmail();
        }
        copyStaffFields(staff, request.getFullName(), email, request.getPhoneNumber(), request.getDateOfBirth());
        staff.setRole("Staff");
        if (hasText(request.getPassword())) {
            staff.setPasswordHash(passwords.encode(request.getPassword()));
        }
        try {
            users.saveAndFlush(staff);
            return OperationResult.success("Cập nhật tài khoản thành công.");
        } catch (DataIntegrityViolationException exception) {
            return duplicateEmail();
        }
    }

    private boolean bookingContainsMovie(Payment payment, int movieId) {
        return payment.getBooking().getTickets().stream()
                .anyMatch(ticket -> ticketMovieId(ticket) == movieId);
    }

    private int ticketMovieId(Ticket ticket) {
        return ticket.getShowtime() == null || ticket.getShowtime().getMovie() == null
                ? Integer.MIN_VALUE : ticket.getShowtime().getMovie().getMovieId();
    }

    private LocalDateTime firstSuccessfulPaymentDate(Ticket ticket) {
        return ticket.getBooking().getPayments().stream()
                .filter(payment -> "Success".equals(payment.getStatus()))
                .map(Payment::getPaymentDate).min(LocalDateTime::compareTo).orElse(null);
    }

    private static boolean isWithin(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        return from == null || (!value.isBefore(from) && value.isBefore(to));
    }

    private static YearMonth parseMonth(String month) {
        if (!hasText(month)) {
            return null;
        }
        try {
            return YearMonth.parse(month, DateTimeFormatter.ofPattern("uuuu-MM"));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private void copyStaffFields(User staff, String fullName, String email,
                                 String phoneNumber, LocalDate dateOfBirth) {
        staff.setFullName(fullName.trim());
        staff.setEmail(email);
        staff.setPhoneNumber(hasText(phoneNumber) ? phoneNumber.trim() : null);
        staff.setDateOfBirth(dateOfBirth);
    }

    private static boolean isValidDateOfBirth(LocalDate dateOfBirth) {
        return dateOfBirth == null || (!dateOfBirth.isAfter(LocalDate.now().minusYears(16))
                && !dateOfBirth.isBefore(EARLIEST_DATE_OF_BIRTH));
    }

    private boolean isPasswordValid(String password, String hash) {
        try {
            return hasText(hash) && passwords.matches(password, hash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static OperationResult duplicateEmail() {
        return new OperationResult(OperationStatus.DUPLICATE_EMAIL, "Email đã tồn tại.");
    }

    private static OperationResult invalidDateOfBirth() {
        return new OperationResult(OperationStatus.INVALID_DATE_OF_BIRTH,
                "Ngày sinh không hợp lệ. Nhân viên phải đủ 16 tuổi và sinh từ năm 1930.");
    }
}
