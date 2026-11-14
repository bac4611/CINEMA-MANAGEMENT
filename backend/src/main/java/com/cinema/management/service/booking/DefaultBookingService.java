package com.cinema.management.service.booking;

import com.cinema.management.model.dto.request.ConcessionRequest;
import com.cinema.management.model.dto.request.SePayWebhookRequest;
import com.cinema.management.model.dto.request.SelectShowtimeRequest;
import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.booking.*;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.payment.*;
import com.cinema.management.model.entity.theater.*;
import com.cinema.management.model.viewmodel.booking.*;
import com.cinema.management.repository.account.UserRepository;
import com.cinema.management.repository.booking.*;
import com.cinema.management.repository.movie.MovieRepository;
import com.cinema.management.repository.movie.MovieCastRepository;
import com.cinema.management.repository.movie.MovieDirectorRepository;
import com.cinema.management.repository.payment.*;
import com.cinema.management.repository.theater.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.hibernate.Hibernate;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
public class DefaultBookingService implements BookingService {
    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);
    private static final int MAXIMUM_SEATS = 5;
    private static final int MAXIMUM_COMBO_QUANTITY = 10;
    private static final Set<String> ACTIVE_HOLD_STATUSES = Set.of("Pending", "PaymentPending", "Processing");
    private static final Pattern PAYMENT_REFERENCE = Pattern.compile(
            "(?<![A-Z0-9])COSMOS(?:\\d{20}|[A-F0-9]{26})(?![A-Z0-9])", Pattern.CASE_INSENSITIVE);

    private final UserRepository users;
    private final MovieRepository movies;
    private final MovieCastRepository movieCasts;
    private final MovieDirectorRepository movieDirectors;
    private final ShowtimeRepository showtimes;
    private final RoomRepository rooms;
    private final SeatRepository seats;
    private final SeatTypePricingRepository pricing;
    private final BookingHoldRepository holds;
    private final BookingSeatHoldRepository seatHolds;
    private final BookingComboHoldRepository comboHolds;
    private final ComboRepository combos;
    private final TicketRepository tickets;
    private final BookingRepository bookings;
    private final BookingComboRepository bookingCombos;
    private final PaymentIntentRepository paymentIntents;
    private final PaymentMethodRepository paymentMethods;
    private final PaymentRepository payments;
    private final ObjectMapper json;
    private final Environment environment;

    public DefaultBookingService(UserRepository users, MovieRepository movies,
                                 MovieCastRepository movieCasts, MovieDirectorRepository movieDirectors,
                                 ShowtimeRepository showtimes, RoomRepository rooms,
                                 SeatRepository seats, SeatTypePricingRepository pricing,
                                 BookingHoldRepository holds, BookingSeatHoldRepository seatHolds,
                                 BookingComboHoldRepository comboHolds, ComboRepository combos,
                                 TicketRepository tickets, BookingRepository bookings,
                                 BookingComboRepository bookingCombos,
                                 PaymentIntentRepository paymentIntents,
                                 PaymentMethodRepository paymentMethods, PaymentRepository payments,
                                 ObjectMapper json, Environment environment) {
        this.users = users;
        this.movies = movies;
        this.movieCasts = movieCasts;
        this.movieDirectors = movieDirectors;
        this.showtimes = showtimes;
        this.rooms = rooms;
        this.seats = seats;
        this.pricing = pricing;
        this.holds = holds;
        this.seatHolds = seatHolds;
        this.comboHolds = comboHolds;
        this.combos = combos;
        this.tickets = tickets;
        this.bookings = bookings;
        this.bookingCombos = bookingCombos;
        this.paymentIntents = paymentIntents;
        this.paymentMethods = paymentMethods;
        this.payments = payments;
        this.json = json;
        this.environment = environment;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingSchedulePageViewModel getSchedule(Integer movieId, LocalDate date) {
        LocalDate today = LocalDate.now();
        LocalDate selectedDate = date == null ? today : date;
        LocalDate lastDate = today.plusDays(13);
        LocalDateTime now = LocalDateTime.now();
        List<Showtime> available = showtimes.findByDateBetweenOrderByDateAscStartTimeAsc(today, lastDate).stream()
                .filter(showtime -> !showtime.getStartTime().isBefore(now))
                .filter(showtime -> movieId == null || movieId.equals(showtime.getMovie().getMovieId()))
                .sorted(Comparator.comparing((Showtime value) -> value.getMovie().getTitle())
                        .thenComparing(Showtime::getDate).thenComparing(Showtime::getStartTime))
                .toList();
        String title = movieId == null ? null : movies.findById(movieId).map(Movie::getTitle).orElse(null);
        if (movieId != null && title == null) {
            return null;
        }

        BookingSchedulePageViewModel model = new BookingSchedulePageViewModel();
        model.setToday(today);
        model.setSelectedDate(selectedDate);
        model.setMovieId(movieId);
        model.setMovieFilterTitle(title);
        model.setDateOptions(IntStream.range(0, 14)
                .mapToObj(offset -> buildDateOption(today.plusDays(offset), today, selectedDate)).toList());

        Map<Integer, List<Showtime>> byMovie = new LinkedHashMap<>();
        available.forEach(showtime -> byMovie.computeIfAbsent(showtime.getMovie().getMovieId(), ignored -> new ArrayList<>())
                .add(showtime));
        List<BookingScheduleMovieViewModel> movieModels = new ArrayList<>();
        for (List<Showtime> values : byMovie.values()) {
            Movie movie = values.getFirst().getMovie();
            var scheduleItems = values.stream().map(this::buildScheduleShowtime).toList();
            BookingScheduleMovieViewModel item = new BookingScheduleMovieViewModel();
            item.setMovieId(movie.getMovieId());
            item.setTitle(movie.getTitle());
            item.setPosterUrl(hasText(movie.getPosterUrl()) ? movie.getPosterUrl() : "/img/poster/poster1.png");
            item.setGenres(movie.getMovieGenres().stream().map(value -> value.getGenre().getName())
                    .reduce((left, right) -> left + ", " + right).orElse(""));
            item.setDurationMinutes(movie.getDuration());
            item.setAgeRating(movie.getAgeRating());
            item.setFormats(scheduleItems.stream().map(BookingScheduleShowtimeViewModel::getFormat)
                    .distinct().toList());
            item.setShowtimes(scheduleItems);
            movieModels.add(item);
        }
        model.setMovies(movieModels);
        return model;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ShowtimePageResult prepareShowtime(int userId, int movieId, UUID currentHoldId) {
        Movie movie = movies.findDetailsById(movieId).orElse(null);
        if (movie == null) {
            return new ShowtimePageResult(Status.NOT_FOUND, null, null, null, "Không tìm thấy phim.");
        }
        BookingHold current = currentHoldId == null ? null : holds.findByIdForUpdate(currentHoldId).orElse(null);
        if (isActive(current) && "PaymentPending".equals(current.getStatus())) {
            String reference = findPendingPaymentReference(currentHoldId, userId).orElse(null);
            if (reference != null) {
                return new ShowtimePageResult(Status.PAYMENT_PENDING, null, currentHoldId, reference, null);
            }
        }
        boolean reusable = isMutable(current) && current.getMovie().getMovieId() == movieId;
        if (current != null && !reusable) {
            releaseHold(current, current.getExpiresAtUtc().isAfter(Instant.now()) ? "Cancelled" : "Expired");
            current = null;
        }
        if (current == null) {
            current = createHold(userId, movie);
        }
        BookingViewModel model = movieView(movie);
        model.setBookingExpiresAtUtc(current.getExpiresAtUtc());
        List<ShowtimeChoiceViewModel> choices = movie.getShowtimes().stream()
                .filter(showtime -> !showtime.getStartTime().isBefore(LocalDateTime.now()))
                .sorted(Comparator.comparing(Showtime::getDate).thenComparing(Showtime::getStartTime))
                .map(this::showtimeChoice).toList();
        model.setShowtimeChoices(choices);
        model.setAvailableFormats(choices.stream().map(ShowtimeChoiceViewModel::getFormat).distinct().sorted().toList());
        model.setAvailableDates(choices.stream().map(ShowtimeChoiceViewModel::getDate).distinct().toList());
        String today = LocalDate.now().toString();
        model.setSelectedDate(model.getAvailableDates().contains(today) ? today
                : model.getAvailableDates().stream().findFirst().orElse(""));
        model.setAvailableTimes(choices.stream().filter(item -> item.getDate().equals(model.getSelectedDate()))
                .map(ShowtimeChoiceViewModel::getTime).distinct().toList());
        model.setSelectedTime(model.getAvailableTimes().stream().findFirst().orElse(""));
        model.setCinemaFormat(model.getAvailableFormats().stream().findFirst().orElse(""));
        return new ShowtimePageResult(Status.SUCCESS, model, current.getBookingHoldId(), null, null);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ShowtimeSelectionResult selectShowtime(int userId, UUID currentHoldId,
                                                  SelectShowtimeRequest request, boolean requireFutureShowtime) {
        if (request.getShowtimeId() <= 0) {
            return selection(Status.INVALID, currentHoldId, request, null, "Vui lòng chọn một suất chiếu trước khi chọn ghế.");
        }
        Showtime showtime = showtimes.findDetailsById(request.getShowtimeId()).orElse(null);
        if (showtime == null || showtime.getMovie().getMovieId() != request.getMovieId()
                || !showtime.getStartTime().isAfter(LocalDateTime.now())) {
            return selection(Status.INVALID, currentHoldId, request, null, "Suất chiếu không tồn tại hoặc đã bắt đầu.");
        }
        BookingHold hold = currentHoldId == null ? null : holds.findByIdForUpdate(currentHoldId).orElse(null);
        if (isActive(hold) && "PaymentPending".equals(hold.getStatus())) {
            String reference = findPendingPaymentReference(currentHoldId, userId).orElse(null);
            if (reference != null) {
                return new ShowtimeSelectionResult(Status.PAYMENT_PENDING, currentHoldId, request.getMovieId(),
                        request.getShowtimeId(), null, null, null, reference, null);
            }
        }
        boolean reusable = isMutable(hold) && hold.getMovie().getMovieId() == request.getMovieId();
        if (hold != null && !reusable) {
            releaseHold(hold, hold.getExpiresAtUtc().isAfter(Instant.now()) ? "Cancelled" : "Expired");
            hold = null;
        }
        if (hold == null) {
            hold = createHold(userId, showtime.getMovie());
        }
        if (hold.getShowtime() != null && !hold.getShowtime().getShowtimeId().equals(showtime.getShowtimeId())) {
            clearHeldResources(hold);
        }
        hold.setShowtime(showtime);
        holds.saveAndFlush(hold);
        return selection(Status.SUCCESS, hold.getBookingHoldId(), request, showtime, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingViewModel> getBookingView(UUID holdId, boolean requireSeats) {
        BookingHold hold = holdId == null ? null : holds.findDetailsById(holdId).orElse(null);
        if (!isMutable(hold) || hold.getShowtime() == null) {
            return Optional.empty();
        }
        BookingViewModel model = buildBookingView(hold);
        return requireSeats && (model.getSelectedSeats().isEmpty() || model.getSelectedSeats().size() > MAXIMUM_SEATS)
                ? Optional.empty() : Optional.of(model);
    }

    @Override
    @Transactional(readOnly = true)
    public ValidationResult validateSelectedSeats(UUID holdId) {
        BookingHold hold = holdId == null ? null : holds.findDetailsById(holdId).orElse(null);
        if (!isMutable(hold)) {
            return invalid(Status.EXPIRED, "Phiên giữ chỗ đã hết hạn.");
        }
        int count = hold.getSeatHolds().size();
        return count >= 1 && count <= MAXIMUM_SEATS
                ? new ValidationResult(Status.SUCCESS, null)
                : invalid(Status.INVALID, "Vui lòng chọn từ 1 đến 5 ghế.");
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public SeatHoldResult updateSeat(UUID holdId, int seatId, boolean select) {
        BookingHold hold = mutableHoldForUpdate(holdId);
        if (hold == null || hold.getShowtime() == null) {
            return new SeatHoldResult(Status.EXPIRED, false, "Phiên giữ chỗ đã hết hạn.");
        }
        Showtime showtime = showtimes.findByIdForUpdate(hold.getShowtime().getShowtimeId()).orElse(null);
        Seat seat = seats.findById(seatId).orElse(null);
        if (showtime == null || seat == null || !seat.getRoom().getRoomId().equals(showtime.getRoom().getRoomId())) {
            return new SeatHoldResult(Status.INVALID, false, "Ghế không hợp lệ.");
        }
        BookingSeatHold existing = seatHolds.findByShowtimeIdAndSeatId(showtime.getShowtimeId(), seatId).orElse(null);
        if (existing != null && !existing.getBookingHoldId().equals(holdId)) {
            BookingHold blocking = holds.findByIdForUpdate(existing.getBookingHoldId()).orElse(null);
            if (blocking != null && !isActive(blocking)) {
                releaseHold(blocking, "Expired");
                existing = null;
            }
        }
        if (!select) {
            if (existing != null && existing.getBookingHoldId().equals(holdId)) {
                seatHolds.delete(existing);
            }
            return new SeatHoldResult(Status.SUCCESS, false, null);
        }
        if (existing != null) {
            return existing.getBookingHoldId().equals(holdId)
                    ? new SeatHoldResult(Status.SUCCESS, true, null)
                    : new SeatHoldResult(Status.CONFLICT, false, "Ghế vừa được người khác giữ.");
        }
        if (seatHolds.findByBookingHoldId(holdId).size() >= MAXIMUM_SEATS) {
            return new SeatHoldResult(Status.INVALID, false, "Chỉ được chọn tối đa 5 ghế.");
        }
        if (tickets.existsByShowtimeShowtimeIdAndSeatSeatId(showtime.getShowtimeId(), seatId)) {
            return new SeatHoldResult(Status.CONFLICT, false, "Ghế đã được bán.");
        }
        BigDecimal multiplier = pricing.findById(seat.getSeatType())
                .map(SeatTypePricing::getMultiplier).orElse(BigDecimal.ONE);
        BookingSeatHold newHold = new BookingSeatHold();
        newHold.setBookingHoldId(holdId);
        newHold.setShowtimeId(showtime.getShowtimeId());
        newHold.setSeatId(seatId);
        newHold.setUnitPrice(showtime.getBasePrice().multiply(multiplier));
        newHold.setHeldAtUtc(Instant.now());
        seatHolds.saveAndFlush(newHold);
        return new SeatHoldResult(Status.SUCCESS, true, null);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ComboHoldResult updateCombo(UUID holdId, int comboId, int change) {
        if (change != 1 && change != -1) {
            return new ComboHoldResult(Status.INVALID, 0, 0, "Thay đổi không hợp lệ.");
        }
        BookingHold hold = mutableHoldForUpdate(holdId);
        if (hold == null) {
            return new ComboHoldResult(Status.EXPIRED, 0, 0, "Phiên giữ chỗ đã hết hạn.");
        }
        Combo combo = combos.findByIdForUpdate(comboId).orElse(null);
        if (combo == null) {
            return new ComboHoldResult(Status.NOT_FOUND, 0, 0, "Không tìm thấy combo.");
        }
        BookingComboHold.Key key = new BookingComboHold.Key(holdId, comboId);
        BookingComboHold selected = comboHolds.findById(key).orElse(null);
        int quantity = selected == null ? 0 : selected.getQuantity();
        if (change == 1) {
            if (quantity >= MAXIMUM_COMBO_QUANTITY) {
                return new ComboHoldResult(Status.INVALID, quantity,
                        availableComboStock(combo, quantity), "Mỗi combo chỉ được chọn tối đa 10.");
            }
            if (combo.getQuantity() - combo.getReservedQuantity() < 1) {
                return new ComboHoldResult(Status.CONFLICT, quantity,
                        availableComboStock(combo, quantity), "Combo đã hết hàng.");
            }
            combo.setReservedQuantity(combo.getReservedQuantity() + 1);
            if (selected == null) {
                selected = new BookingComboHold();
                selected.setBookingHoldId(holdId);
                selected.setComboId(comboId);
                selected.setUnitPrice(combo.getComboPrice());
            }
            selected.setQuantity(quantity + 1);
            comboHolds.save(selected);
        } else if (selected != null && quantity > 0) {
            if (combo.getReservedQuantity() < 1) {
                throw new IllegalStateException("ReservedQuantity của combo không hợp lệ.");
            }
            combo.setReservedQuantity(combo.getReservedQuantity() - 1);
            selected.setQuantity(quantity - 1);
            if (selected.getQuantity() == 0) {
                comboHolds.delete(selected);
            } else {
                comboHolds.save(selected);
            }
        }
        combos.save(combo);
        int updatedQuantity = Math.max(0, quantity + change);
        return new ComboHoldResult(Status.SUCCESS, updatedQuantity,
                availableComboStock(combo, updatedQuantity), null);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ValidationResult clearCombos(UUID holdId) {
        BookingHold hold = mutableHoldForUpdate(holdId);
        if (hold == null) {
            return invalid(Status.EXPIRED, "Phiên giữ chỗ đã hết hạn.");
        }
        releaseComboReservations(holdId, false);
        return new ValidationResult(Status.SUCCESS, null);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PaymentIntentResult startPayment(int userId, UUID holdId) {
        // Lock the hold before checking an existing payment, including PaymentPending.
        BookingHold hold = holdId == null ? null : holds.findByIdForUpdate(holdId).orElse(null);
        if (hold == null) {
            return new PaymentIntentResult(Status.EXPIRED, null, null, "Phiên giữ chỗ không hợp lệ hoặc đã hết hạn.");
        }
        if (hold.getUser() != null && hold.getUser().getUserId() != userId) {
            return new PaymentIntentResult(Status.UNAUTHORIZED, null, null, "Phiên giữ chỗ không thuộc người dùng hiện tại.");
        }
        if (!isActive(hold)) {
            if (Set.of("Pending", "PaymentPending").contains(hold.getStatus())) releaseHold(hold, "Expired");
            return new PaymentIntentResult(Status.EXPIRED, null, null, "Phiên giữ chỗ đã hết hạn.");
        }
        if ((!isMutable(hold) && !"PaymentPending".equals(hold.getStatus()))
                || hold.getShowtime() == null || hold.getSeatHolds().isEmpty()
                || hold.getSeatHolds().size() > MAXIMUM_SEATS) {
            return new PaymentIntentResult(Status.INVALID, null, null, "Phiên giữ chỗ không hợp lệ.");
        }
        PaymentIntent existing = paymentIntents.findByBookingHoldBookingHoldIdAndStatus(holdId, "Pending").orElse(null);
        if (existing != null) {
            if (!existing.getUser().getUserId().equals(userId)) {
                return new PaymentIntentResult(Status.UNAUTHORIZED, null, null, "Phiên thanh toán không thuộc người dùng hiện tại.");
            }
            if (!existing.getExpiresAtUtc().isAfter(Instant.now())) {
                expireIntent(existing);
                return new PaymentIntentResult(Status.EXPIRED, null, null, "Phiên thanh toán đã hết hạn.");
            }
            return new PaymentIntentResult(Status.SUCCESS, existing.getPaymentReference(), existing.getExpiresAtUtc(), null);
        }
        if ("PaymentPending".equals(hold.getStatus())) {
            return new PaymentIntentResult(Status.INVALID, null, null, "Không tìm thấy phiên thanh toán đang chờ.");
        }
        BigDecimal amount = normalizeVnd(hold.getSeatHolds().stream().map(BookingSeatHold::getUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add).add(hold.getComboHolds().stream()
                        .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        String reference = generatePaymentReference();
        hold.setUser(users.getReferenceById(userId));
        hold.setStatus("PaymentPending");
        PaymentIntent intent = new PaymentIntent();
        intent.setUser(hold.getUser());
        intent.setMovie(hold.getMovie());
        intent.setShowtime(hold.getShowtime());
        intent.setBookingHold(hold);
        intent.setPaymentReference(reference);
        intent.setExpectedAmount(amount);
        intent.setStatus("Pending");
        intent.setSelectedSeatCodes(hold.getSeatHolds().stream().map(item -> item.getSeat().getSeatCode())
                .sorted().reduce((left, right) -> left + "," + right).orElse(""));
        intent.setSelectedCombosJson(serializeCombos(hold.getComboHolds()));
        intent.setCreatedAtUtc(Instant.now());
        intent.setExpiresAtUtc(hold.getExpiresAtUtc());
        paymentIntents.saveAndFlush(intent);
        return new PaymentIntentResult(Status.SUCCESS, reference, hold.getExpiresAtUtc(), null);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PaymentViewResult getPaymentPage(int userId, String paymentReference) {
        PaymentIntent intent = paymentIntents.findByPaymentReferenceAndUserUserId(paymentReference, userId).orElse(null);
        if (intent == null) {
            return new PaymentViewResult(Status.NOT_FOUND, null, null, "Phiên thanh toán không tồn tại.");
        }
        if ("Success".equals(intent.getStatus()) && intent.getBooking() != null) {
            return new PaymentViewResult(Status.SUCCESS, null, intent.getBooking().getBookingId(), null);
        }
        if (!intent.getExpiresAtUtc().isAfter(Instant.now())) {
            expireIntent(intent);
            return new PaymentViewResult(Status.EXPIRED, null, null, "Phiên thanh toán đã hết hạn.");
        }
        BookingHold hold = intent.getBookingHold() == null ? null
                : holds.findDetailsById(intent.getBookingHold().getBookingHoldId()).orElse(null);
        if (hold == null || !"PaymentPending".equals(hold.getStatus())) {
            return new PaymentViewResult(Status.INVALID, null, null, "Dữ liệu giữ chỗ không còn hợp lệ.");
        }
        return new PaymentViewResult(Status.SUCCESS, paymentPage(intent, hold), null, null);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PaymentCompletionResult completeDevelopmentPayment(int userId, String paymentReference) {
        PaymentIntent intent = paymentIntents.findByPaymentReferenceAndUserUserId(paymentReference, userId).orElse(null);
        return intent == null ? new PaymentCompletionResult(Status.NOT_FOUND, null, "Phiên thanh toán không tồn tại.")
                : completePayment(intent, "Development", null, null, "Development payment simulation", null);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WebhookResult processSePayWebhook(SePayWebhookRequest request, String rawPayload) {
        if (request == null || request.getId() <= 0) {
            return new WebhookResult(Status.INVALID, "Invalid payload");
        }
        if (!"in".equalsIgnoreCase(request.getTransferType())) {
            return new WebhookResult(Status.SUCCESS, null);
        }
        String reference = extractPaymentReference(request.getCode(), request.getContent(), request.getDescription());
        PaymentIntent intent = reference == null ? null : paymentIntents.findByPaymentReferenceForUpdate(reference).orElse(null);
        if (intent == null) {
            return new WebhookResult(Status.NOT_FOUND, "Payment reference not found");
        }
        if (Objects.equals(intent.getSePayTransactionId(), request.getId()) && "Success".equals(intent.getStatus())) {
            return new WebhookResult(Status.SUCCESS, null);
        }
        if (paymentIntents.existsBySePayTransactionIdAndPaymentIntentIdNot(request.getId(), intent.getPaymentIntentId())) {
            return new WebhookResult(Status.SUCCESS, null);
        }
        if (request.getTransferAmount() == null
                || normalizeVnd(request.getTransferAmount()).compareTo(normalizeVnd(intent.getExpectedAmount())) != 0) {
            return new WebhookResult(Status.INVALID, "Transfer amount does not match expected amount");
        }
        PaymentCompletionResult result = completePayment(intent, "SePay", request.getId(),
                request.getReferenceCode(), request.getContent(), rawPayload);
        return new WebhookResult(result.status(), result.message());
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PaymentStatusResult getPaymentStatus(int userId, String paymentReference) {
        PaymentIntent intent = paymentIntents.findByPaymentReferenceAndUserUserId(paymentReference, userId).orElse(null);
        if (intent == null) {
            return new PaymentStatusResult(Status.NOT_FOUND, null);
        }
        if ("Pending".equals(intent.getStatus()) && !intent.getExpiresAtUtc().isAfter(Instant.now())) {
            expireIntent(intent);
            return new PaymentStatusResult(Status.EXPIRED, null);
        }
        Status status = "Success".equals(intent.getStatus()) ? Status.SUCCESS
                : "Expired".equals(intent.getStatus()) ? Status.EXPIRED : Status.PAYMENT_PENDING;
        return new PaymentStatusResult(status, intent.getBooking() == null ? null : intent.getBooking().getBookingId());
    }

    @Override
    @Transactional(readOnly = true)
    public HoldState getHoldState(UUID holdId) {
        BookingHold hold = holdId == null ? null : holds.findById(holdId).orElse(null);
        if (hold == null) return new HoldState(Status.NOT_FOUND, null, null);
        if (!isActive(hold)) return new HoldState(Status.EXPIRED, hold.getMovie().getMovieId(),
                hold.getShowtime() == null ? null : hold.getShowtime().getShowtimeId());
        return new HoldState("PaymentPending".equals(hold.getStatus()) ? Status.PAYMENT_PENDING : Status.SUCCESS,
                hold.getMovie().getMovieId(), hold.getShowtime() == null ? null : hold.getShowtime().getShowtimeId());
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void expireHold(UUID holdId) {
        BookingHold hold = holdId == null ? null : holds.findByIdForUpdate(holdId).orElse(null);
        if (hold != null) releaseHold(hold, "Expired");
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void cancelHold(UUID holdId) {
        BookingHold hold = holdId == null ? null : holds.findByIdForUpdate(holdId).orElse(null);
        if (hold != null) releaseHold(hold, "Cancelled");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> getCompletedBooking(int userId, int bookingId) {
        Optional<Booking> result = bookings.findDetailsByIdAndUserId(bookingId, userId);
        result.ifPresent(booking -> {
            Hibernate.initialize(booking.getTickets());
            booking.getTickets().forEach(ticket -> {
                Hibernate.initialize(ticket.getShowtime());
                Hibernate.initialize(ticket.getShowtime().getMovie());
                Hibernate.initialize(ticket.getSeat());
            });
            Hibernate.initialize(booking.getBookingCombos());
            booking.getBookingCombos().forEach(item -> Hibernate.initialize(item.getCombo()));
            Hibernate.initialize(booking.getPayments());
            booking.getPayments().forEach(payment -> Hibernate.initialize(payment.getPaymentMethod()));
        });
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findPendingPaymentReference(UUID holdId, Integer userId) {
        return paymentIntents.findByBookingHoldBookingHoldIdAndStatus(holdId, "Pending")
                .filter(intent -> userId == null || intent.getUser().getUserId().equals(userId))
                .map(PaymentIntent::getPaymentReference);
    }

    private PaymentCompletionResult completePayment(PaymentIntent supplied, String methodName, Long transactionId,
                                                    String referenceCode, String content, String rawPayload) {
        PaymentIntent intent = paymentIntents.findByPaymentReferenceForUpdate(supplied.getPaymentReference()).orElse(null);
        if (intent == null) return completion(Status.NOT_FOUND, null, "Không tìm thấy phiên thanh toán.");
        if ("Success".equals(intent.getStatus()) && intent.getBooking() != null)
            return completion(Status.SUCCESS, intent.getBooking().getBookingId(), null);
        if (intent.getBookingHold() == null) return completion(Status.INVALID, null, "Phiên thanh toán chưa liên kết giữ chỗ.");
        BookingHold hold = holds.findByIdForUpdate(intent.getBookingHold().getBookingHoldId()).orElse(null);
        if (hold == null) return completion(Status.INVALID, null, "Không tìm thấy dữ liệu giữ chỗ.");
        if (!intent.getExpiresAtUtc().isAfter(Instant.now())) {
            expireIntent(intent);
            return completion(Status.EXPIRED, null, "Phiên thanh toán đã hết hạn.");
        }
        if (!"PaymentPending".equals(hold.getStatus()))
            return completion(Status.CONFLICT, null, "Giữ chỗ không còn ở trạng thái thanh toán.");
        hold.setStatus("Processing");
        if (hold.getSeatHolds().isEmpty() || hold.getSeatHolds().size() > MAXIMUM_SEATS)
            return failedCompletion(hold, Status.INVALID, "Số lượng ghế không hợp lệ.");
        BigDecimal total = normalizeVnd(hold.getSeatHolds().stream().map(BookingSeatHold::getUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add).add(hold.getComboHolds().stream()
                        .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        if (total.compareTo(normalizeVnd(intent.getExpectedAmount())) != 0)
            return failedCompletion(hold, Status.CONFLICT, "Số tiền không khớp với dữ liệu giữ chỗ.");
        Showtime showtime = showtimes.findByIdForUpdate(hold.getShowtime().getShowtimeId()).orElseThrow();
        for (BookingSeatHold item : hold.getSeatHolds()) {
            if (tickets.existsByShowtimeShowtimeIdAndSeatSeatId(showtime.getShowtimeId(), item.getSeatId()))
                return failedCompletion(hold, Status.CONFLICT, "Một hoặc nhiều ghế đã được bán.");
        }
        for (BookingComboHold item : hold.getComboHolds()) {
            Combo combo = combos.findByIdForUpdate(item.getComboId()).orElse(null);
            if (combo == null || combo.getQuantity() < item.getQuantity()
                    || combo.getReservedQuantity() < item.getQuantity())
                return failedCompletion(hold, Status.CONFLICT, "Tồn kho combo không còn hợp lệ.");
            combo.setQuantity(combo.getQuantity() - item.getQuantity());
            combo.setReservedQuantity(combo.getReservedQuantity() - item.getQuantity());
            combos.save(combo);
        }
        PaymentMethod method = paymentMethods.findByMethodNameIgnoreCase(methodName).orElseGet(() -> {
            PaymentMethod value = new PaymentMethod(); value.setMethodName(methodName); return paymentMethods.save(value);
        });
        Booking booking = new Booking();
        booking.setUser(intent.getUser());
        booking.setBookingDate(LocalDateTime.now());
        booking.setTotalAmount(total);
        booking.setStatus("Confirmed");
        bookings.saveAndFlush(booking);
        for (BookingSeatHold item : hold.getSeatHolds()) {
            Ticket ticket = new Ticket();
            ticket.setBooking(booking); ticket.setShowtime(showtime); ticket.setSeat(item.getSeat());
            ticket.setTicketCode("TKT-" + UUID.randomUUID().toString().replace("-", ""));
            tickets.save(ticket);
        }
        for (BookingComboHold item : hold.getComboHolds()) {
            BookingCombo value = new BookingCombo();
            value.setBookingId(booking.getBookingId()); value.setComboId(item.getComboId());
            value.setQuantity(item.getQuantity()); value.setUnitPrice(item.getUnitPrice());
            bookingCombos.save(value);
        }
        Payment payment = new Payment();
        payment.setBooking(booking); payment.setPaymentMethod(method); payment.setAmount(total);
        payment.setPaymentDate(LocalDateTime.now()); payment.setStatus("Success"); payments.save(payment);
        intent.setBooking(booking); intent.setStatus("Success"); intent.setSePayTransactionId(transactionId);
        intent.setSePayReferenceCode(referenceCode); intent.setSePayContent(content);
        intent.setWebhookPayload(rawPayload); intent.setPaidAtUtc(Instant.now());
        paymentIntents.save(intent);
        seatHolds.deleteByBookingHoldId(hold.getBookingHoldId());
        comboHolds.deleteByBookingHoldId(hold.getBookingHoldId());
        hold.setStatus("Completed"); holds.save(hold);
        return completion(Status.SUCCESS, booking.getBookingId(), null);
    }

    private PaymentCompletionResult failedCompletion(BookingHold hold, Status status, String message) {
        hold.setStatus("PaymentPending");
        holds.save(hold);
        return completion(status, null, message);
    }

    private BookingViewModel buildBookingView(BookingHold hold) {
        Showtime showtime = hold.getShowtime();
        List<Seat> roomSeats = seats.findByRoomRoomIdOrderBySeatCode(showtime.getRoom().getRoomId());
        Set<Integer> occupied = new HashSet<>(seatHolds.findActiveHeldSeatIds(showtime.getShowtimeId()));
        occupied.removeAll(hold.getSeatHolds().stream().map(BookingSeatHold::getSeatId).toList());
        tickets.findAll().stream().filter(item -> item.getShowtime().getShowtimeId().equals(showtime.getShowtimeId()))
                .map(item -> item.getSeat().getSeatId()).forEach(occupied::add);
        Map<String, BigDecimal> multipliers = new HashMap<>();
        pricing.findAll().forEach(item -> multipliers.put(item.getSeatType(), item.getMultiplier()));
        Map<Integer, BookingSeatHold> ownSeats = new HashMap<>();
        hold.getSeatHolds().forEach(item -> ownSeats.put(item.getSeatId(), item));
        Map<Integer, BookingComboHold> ownCombos = new HashMap<>();
        hold.getComboHolds().forEach(item -> ownCombos.put(item.getComboId(), item));
        BookingViewModel model = new BookingViewModel();
        model.setMovieId(hold.getMovie().getMovieId()); model.setMovieTitle(hold.getMovie().getTitle());
        model.setShowtimeId(showtime.getShowtimeId()); model.setSelectedDate(showtime.getDate().toString());
        model.setSelectedTime(showtime.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        model.setCinemaFormat(presentationFormat(showtime)); model.setBookingExpiresAtUtc(hold.getExpiresAtUtc());
        model.setSelectedSeats(hold.getSeatHolds().stream().map(item -> item.getSeat().getSeatCode()).sorted().toList());
        model.setOccupiedSeats(roomSeats.stream().filter(item -> occupied.contains(item.getSeatId()))
                .map(Seat::getSeatCode).toList());
        model.setStandardTicketPrice(showtime.getBasePrice().multiply(multipliers.getOrDefault("Regular", BigDecimal.ONE)));
        model.setVipTicketPrice(showtime.getBasePrice().multiply(multipliers.getOrDefault("VIP", BigDecimal.ONE)));
        model.setSweetboxTicketPrice(showtime.getBasePrice().multiply(multipliers.getOrDefault("Couple", BigDecimal.ONE)));
        model.setSeatChoices(roomSeats.stream().map(seat -> {
            SeatChoiceViewModel choice = new SeatChoiceViewModel(); choice.setSeatId(seat.getSeatId());
            choice.setSeatCode(seat.getSeatCode()); choice.setSeatType(seat.getSeatType());
            choice.setOccupied(occupied.contains(seat.getSeatId())); choice.setSelected(ownSeats.containsKey(seat.getSeatId()));
            choice.setPrice(ownSeats.containsKey(seat.getSeatId()) ? ownSeats.get(seat.getSeatId()).getUnitPrice()
                    : showtime.getBasePrice().multiply(multipliers.getOrDefault(seat.getSeatType(), BigDecimal.ONE)));
            return choice;
        }).toList());
        model.setConcessions(combos.findAll().stream().sorted(Comparator.comparing(Combo::getComboName)).map(combo -> {
            BookingComboHold own = ownCombos.get(combo.getComboId());
            ConcessionItemViewModel item = new ConcessionItemViewModel(); item.setId(combo.getComboId());
            item.setName(combo.getComboName()); item.setPrice(own == null ? combo.getComboPrice() : own.getUnitPrice());
            item.setSelectedQuantity(own == null ? 0 : own.getQuantity());
            item.setStockQuantity(Math.max(0, combo.getQuantity() - combo.getReservedQuantity()
                    + (own == null ? 0 : own.getQuantity()))); return item;
        }).toList());
        return model;
    }

    private PaymentPageViewModel paymentPage(PaymentIntent intent, BookingHold hold) {
        PaymentPageViewModel model = new PaymentPageViewModel();
        model.setPaymentReference(intent.getPaymentReference()); model.setMovieTitle(hold.getMovie().getTitle());
        model.setSelectedDate(hold.getShowtime().getDate().toString());
        model.setSelectedTime(hold.getShowtime().getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        model.setCinemaFormat(presentationFormat(hold.getShowtime()));
        model.setSelectedSeats(hold.getSeatHolds().stream().map(item -> item.getSeat().getSeatCode()).sorted().toList());
        model.setConcessions(hold.getComboHolds().stream().map(value -> {
            ConcessionItemViewModel item = new ConcessionItemViewModel(); item.setId(value.getComboId());
            item.setName(value.getCombo() == null ? "Combo " + value.getComboId() : value.getCombo().getComboName());
            item.setPrice(value.getUnitPrice()); item.setSelectedQuantity(value.getQuantity()); return item;
        }).toList());
        model.setTicketSubtotal(hold.getSeatHolds().stream().map(BookingSeatHold::getUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.setConcessionSubtotal(hold.getComboHolds().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.setTotalAmount(intent.getExpectedAmount()); model.setQrImageUrl(qrUrl(intent));
        model.setExpiresAtUtc(intent.getExpiresAtUtc());
        model.setShowDevelopmentPaymentButton(Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("dev") || profile.equalsIgnoreCase("development")));
        return model;
    }

    private void expireIntent(PaymentIntent intent) {
        if (!"Pending".equals(intent.getStatus())) return;
        intent.setStatus("Expired"); intent.setExpiresAtUtc(Instant.now()); paymentIntents.save(intent);
        if (intent.getBookingHold() != null) {
            BookingHold hold = holds.findByIdForUpdate(intent.getBookingHold().getBookingHoldId()).orElse(null);
            if (hold != null) releaseHold(hold, "Expired");
        }
    }

    private void releaseHold(BookingHold hold, String status) {
        if (!Set.of("Cancelled", "Expired").contains(status)) throw new IllegalArgumentException("Invalid final status");
        if (!Set.of("Pending", "PaymentPending", "Processing").contains(hold.getStatus())) return;
        hold.setStatus("Releasing"); holds.saveAndFlush(hold);
        releaseComboReservations(hold.getBookingHoldId(), true);
        seatHolds.deleteByBookingHoldId(hold.getBookingHoldId());
        paymentIntents.findByBookingHoldBookingHoldIdAndStatus(hold.getBookingHoldId(), "Pending").ifPresent(intent -> {
            intent.setStatus("Expired"); intent.setExpiresAtUtc(Instant.now()); paymentIntents.save(intent);
        });
        hold.setStatus(status); holds.save(hold);
    }

    private void clearHeldResources(BookingHold hold) {
        releaseComboReservations(hold.getBookingHoldId(), true);
        seatHolds.deleteByBookingHoldId(hold.getBookingHoldId());
    }

    private void releaseComboReservations(UUID holdId, boolean delete) {
        for (BookingComboHold item : comboHolds.findByBookingHoldId(holdId)) {
            Combo combo = combos.findByIdForUpdate(item.getComboId()).orElseThrow();
            if (combo.getReservedQuantity() < item.getQuantity())
                throw new IllegalStateException("ReservedQuantity của combo không hợp lệ.");
            combo.setReservedQuantity(combo.getReservedQuantity() - item.getQuantity()); combos.save(combo);
        }
        if (delete) comboHolds.deleteByBookingHoldId(holdId);
        else comboHolds.deleteByBookingHoldId(holdId);
    }

    private BookingHold createHold(int userId, Movie movie) {
        BookingHold hold = new BookingHold(); hold.setBookingHoldId(UUID.randomUUID()); hold.setMovie(movie);
        hold.setUser(users.getReferenceById(userId)); hold.setStatus("Pending"); hold.setCreatedAtUtc(Instant.now());
        hold.setExpiresAtUtc(Instant.now().plus(HOLD_DURATION)); return holds.save(hold);
    }

    private BookingHold mutableHoldForUpdate(UUID holdId) {
        BookingHold hold = holdId == null ? null : holds.findByIdForUpdate(holdId).orElse(null);
        if (hold != null && !isActive(hold) && Set.of("Pending", "PaymentPending").contains(hold.getStatus())) {
            releaseHold(hold, "Expired"); return null;
        }
        return isMutable(hold) ? hold : null;
    }

    private boolean isActive(BookingHold hold) {
        return hold != null && hold.getExpiresAtUtc() != null && hold.getExpiresAtUtc().isAfter(Instant.now())
                && ACTIVE_HOLD_STATUSES.contains(hold.getStatus());
    }

    private boolean isMutable(BookingHold hold) { return isActive(hold) && "Pending".equals(hold.getStatus()); }

    private BookingScheduleShowtimeViewModel buildScheduleShowtime(Showtime showtime) {
        Room room = rooms.findWithSeatsByRoomId(showtime.getRoom().getRoomId()).orElse(showtime.getRoom());
        int total = room.getSeats().size();
        int remaining = Math.max(0, total - (int) tickets.countByShowtimeShowtimeId(showtime.getShowtimeId())
                - seatHolds.findActiveHeldSeatIds(showtime.getShowtimeId()).size());
        boolean low = remaining > 0 && (remaining <= 20 || remaining <= total * .15);
        BookingScheduleShowtimeViewModel item = new BookingScheduleShowtimeViewModel();
        item.setShowtimeId(showtime.getShowtimeId()); item.setMovieId(showtime.getMovie().getMovieId());
        item.setDate(showtime.getDate().toString()); item.setDateLabel(showtime.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        item.setTime(showtime.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"))); item.setFormat(resolveFormat(showtime));
        item.setRoomName(room.getRoomName()); item.setRemainingSeats(remaining); item.setTotalSeats(total);
        item.setLate(showtime.getStartTime().toLocalTime().compareTo(LocalTime.of(22, 0)) >= 0);
        item.setLowAvailability(low); item.setSoldOut(remaining <= 0);
        item.setAvailabilityLabel(remaining <= 0 ? "Hết vé" : low ? "Còn ít ghế" : remaining + " ghế trống");
        return item;
    }

    private BookingScheduleDateOption buildDateOption(LocalDate date, LocalDate today, LocalDate selected) {
        BookingScheduleDateOption item = new BookingScheduleDateOption(); item.setDate(date); item.setValue(date.toString());
        item.setDateLabel(date.format(DateTimeFormatter.ofPattern("dd/MM")));
        item.setWeekdayLabel(switch (date.getDayOfWeek()) { case MONDAY -> "T2"; case TUESDAY -> "T3";
            case WEDNESDAY -> "T4"; case THURSDAY -> "T5"; case FRIDAY -> "T6"; case SATURDAY -> "T7"; default -> "CN"; });
        item.setToday(date.equals(today)); item.setSelected(date.equals(selected)); return item;
    }

    private BookingViewModel movieView(Movie movie) {
        BookingViewModel model = new BookingViewModel(); model.setMovieId(movie.getMovieId()); model.setMovieTitle(movie.getTitle());
        model.setSynopsis(movie.getSynopsis()); model.setAgeRating(movie.getAgeRating()); model.setDurationMinutes(movie.getDuration());
        model.setPosterUrl(movie.getPosterUrl()); model.setGenre(movie.getMovieGenres().stream().map(value -> value.getGenre().getName())
                .reduce((a,b) -> a + ", " + b).orElse(""));
        model.setLanguage(movie.getLanguage() == null ? "" : movie.getLanguage().getLanguageName());
        model.setCountry(movie.getCountry() == null ? "" : movie.getCountry().getCountryName());
        model.setDirector(String.join(", ", movieDirectors.findPersonNamesByMovieId(movie.getMovieId())));
        model.setCast(String.join(", ", movieCasts.findPersonNamesByMovieId(movie.getMovieId())));
        return model;
    }

    private ShowtimeChoiceViewModel showtimeChoice(Showtime showtime) {
        ShowtimeChoiceViewModel item = new ShowtimeChoiceViewModel(); item.setShowtimeId(showtime.getShowtimeId());
        item.setDate(showtime.getDate().toString()); item.setTime(showtime.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        item.setFormat(presentationFormat(showtime)); item.setRoomName(showtime.getRoom().getRoomName()); return item;
    }

    private ShowtimeSelectionResult selection(Status status, UUID holdId, SelectShowtimeRequest request,
                                               Showtime showtime, String message) {
        return new ShowtimeSelectionResult(status, holdId, request.getMovieId(), request.getShowtimeId(),
                showtime == null ? null : presentationFormat(showtime), showtime == null ? null : showtime.getDate().toString(),
                showtime == null ? null : showtime.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")), null, message);
    }

    private String resolveFormat(Showtime showtime) {
        String voice = showtime.getMovie().getLanguage() != null
                && showtime.getMovie().getLanguage().getLanguageName().toLowerCase(Locale.ROOT).contains("vi")
                ? "LỒNG TIẾNG" : "PHỤ ĐỀ";
        return (showtime.getRoom().getRoomName().toUpperCase(Locale.ROOT).contains("IMAX") ? "IMAX 2D" : "2D") + " " + voice;
    }

    private String presentationFormat(Showtime showtime) {
        return showtime.getRoom().getRoomName().toUpperCase(Locale.ROOT).contains("IMAX") ? "IMAX" : "2D";
    }

    private String serializeCombos(List<BookingComboHold> values) {
        try {
            return json.writeValueAsString(values.stream().map(value -> {
                ConcessionRequest item = new ConcessionRequest(); item.setComboId(value.getComboId());
                item.setQuantity(value.getQuantity()); return item;
            }).toList());
        } catch (JacksonException exception) { throw new IllegalStateException("Cannot serialize combos", exception); }
    }

    private String generatePaymentReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String value = "COSMOS" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(Instant.now())
                    + (100000 + new Random().nextInt(900000));
            if (!paymentIntents.existsByPaymentReference(value)) return value;
        }
        return ("COSMOS" + UUID.randomUUID().toString().replace("-", "")).substring(0, 32).toUpperCase(Locale.ROOT);
    }

    private String qrUrl(PaymentIntent intent) {
        String account = environment.getProperty("sepay.bank-account-number");
        String bank = environment.getProperty("sepay.bank-code");
        if (!hasText(account) || !hasText(bank)) return "/img/poster/QR.png";
        return UriComponentsBuilder.fromUriString("https://vietqr.app/img").queryParam("acc", account)
                .queryParam("bank", bank).queryParam("amount", normalizeVnd(intent.getExpectedAmount()).toPlainString())
                .queryParam("des", intent.getPaymentReference()).build().encode().toUriString();
    }

    private String extractPaymentReference(String... values) {
        for (String value : values) if (hasText(value)) {
            Matcher matcher = PAYMENT_REFERENCE.matcher(value.toUpperCase(Locale.ROOT));
            if (matcher.find()) return matcher.group().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private int availableComboStock(Combo combo, int selected) {
        return Math.max(0, combo.getQuantity() - combo.getReservedQuantity() + selected);
    }

    private BigDecimal normalizeVnd(BigDecimal value) { return value.setScale(0, RoundingMode.DOWN); }
    private ValidationResult invalid(Status status, String message) { return new ValidationResult(status, message); }
    private PaymentCompletionResult completion(Status status, Integer bookingId, String message) {
        return new PaymentCompletionResult(status, bookingId, message);
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
