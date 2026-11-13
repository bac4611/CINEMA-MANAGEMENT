package com.cinema.management.service.staff;

import com.cinema.management.model.dto.request.SaveScheduleItemRequest;
import com.cinema.management.model.dto.request.SaveScheduleRequest;
import com.cinema.management.model.dto.request.StaffMovieRequest;
import com.cinema.management.model.entity.booking.*;
import com.cinema.management.model.entity.movie.*;
import com.cinema.management.model.entity.payment.Payment;
import com.cinema.management.model.entity.theater.*;
import com.cinema.management.model.viewmodel.admin.ConcessionsViewModel;
import com.cinema.management.model.viewmodel.home.MovieViewModel;
import com.cinema.management.model.viewmodel.staff.*;
import com.cinema.management.repository.account.UserRepository;
import com.cinema.management.repository.booking.*;
import com.cinema.management.repository.movie.*;
import com.cinema.management.repository.payment.PaymentRepository;
import com.cinema.management.repository.theater.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DefaultStaffService implements StaffService {
    private static final LocalTime OPENING = LocalTime.of(8, 0);
    private static final LocalTime CLOSING = LocalTime.of(23, 0);
    private static final String DEFAULT_POSTER = "/img/poster/yourname400x600.png";
    private static final String DEFAULT_TRAILER = "#";
    private static final String FORMAT_MARKER = "\n[COSMOS_FORMATS:";

    private final MovieRepository movies; private final GenreRepository genres;
    private final MovieGenreRepository movieGenres; private final MovieCastRepository movieCasts;
    private final MovieDirectorRepository movieDirectors; private final PersonRepository people;
    private final LanguageRepository languages; private final CountryRepository countries;
    private final ShowtimeRepository showtimes; private final RoomRepository rooms; private final SeatRepository seats;
    private final TicketRepository tickets; private final ComboRepository combos; private final PaymentRepository payments;

    public DefaultStaffService(MovieRepository movies, GenreRepository genres, MovieGenreRepository movieGenres,
                               MovieCastRepository movieCasts, MovieDirectorRepository movieDirectors,
                               PersonRepository people, LanguageRepository languages, CountryRepository countries,
                               ShowtimeRepository showtimes, RoomRepository rooms, SeatRepository seats,
                               TicketRepository tickets, ComboRepository combos, PaymentRepository payments) {
        this.movies = movies; this.genres = genres; this.movieGenres = movieGenres; this.movieCasts = movieCasts;
        this.movieDirectors = movieDirectors; this.people = people; this.languages = languages; this.countries = countries;
        this.showtimes = showtimes; this.rooms = rooms; this.seats = seats; this.tickets = tickets;
        this.combos = combos; this.payments = payments;
    }

    @Override @Transactional(readOnly = true)
    public StaffScheduleViewModel getSchedulePage() {
        StaffScheduleViewModel result = new StaffScheduleViewModel();
        result.setMovies(movies.findAll().stream()
                .sorted(Comparator.comparing(Movie::getReleaseDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapMovie).toList());
        result.setRooms(roomSummaries());
        return result;
    }

    @Override @Transactional(readOnly = true)
    public List<ScheduleItem> getSchedule(LocalDate date) {
        if (date == null) return List.of();
        return showtimes.findByDateOrderByRoomRoomIdAscStartTimeAsc(date).stream().map(showtime ->
                new ScheduleItem(showtime.getShowtimeId(), showtime.getMovie().getMovieId(), showtime.getMovie().getTitle(),
                        showtime.getRoom().getRoomId(), showtime.getRoom().getRoomId(),
                        showtime.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                        showtime.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                        Duration.between(showtime.getStartTime(), showtime.getEndTime()).toMinutes(),
                        showtime.getMovie().getReleaseDate(), extractFormats(showtime.getMovie().getTrailer()),
                        showtime.getBasePrice())).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<ScreeningRoomStateViewModel> getScreeningRoomState(int roomId, LocalDate date, Integer showtimeId) {
        Room room = rooms.findById(roomId).orElse(null);
        if (room == null) return Optional.empty();
        List<Showtime> roomShowtimes = showtimes.findByRoomRoomIdAndDateOrderByStartTime(roomId,
                date == null ? LocalDate.now() : date);
        if (showtimeId != null && roomShowtimes.stream().noneMatch(item -> item.getShowtimeId().equals(showtimeId))) {
            throw new IllegalArgumentException("Suất chiếu không thuộc phòng đã chọn.");
        }
        Integer selected = showtimeId != null ? showtimeId
                : roomShowtimes.stream().findFirst().map(Showtime::getShowtimeId).orElse(null);
        List<Ticket> selectedTickets = selected == null ? List.of() : tickets.findByShowtimeShowtimeId(selected);
        Map<Integer, String> states = new HashMap<>();
        for (Ticket ticket : selectedTickets) {
            String state = "Confirmed".equals(ticket.getBooking().getStatus()) ? "reserved"
                    : "Pending".equals(ticket.getBooking().getStatus()) ? "pending" : "available";
            states.merge(ticket.getSeat().getSeatId(), state,
                    (oldValue, newValue) -> "reserved".equals(oldValue) || "reserved".equals(newValue) ? "reserved" : newValue);
        }
        List<ScreeningRoomStateViewModel.SeatItem> seatItems = seats.findByRoomRoomIdOrderBySeatCode(roomId).stream()
                .map(seat -> screeningSeat(seat, states.getOrDefault(seat.getSeatId(), "available"))).toList();
        ScreeningRoomStateViewModel result = new ScreeningRoomStateViewModel();
        result.setRoomId(roomId); result.setSelectedShowtimeId(selected);
        result.setShowtimes(roomShowtimes.stream().map(this::screeningShowtime).toList());
        result.setRows(screeningRows(seatItems)); result.setCounts(screeningCounts(seatItems));
        result.setReservations(screeningReservations(selectedTickets));
        return Optional.of(result);
    }

    @Override @Transactional(isolation = Isolation.SERIALIZABLE)
    public OperationResult saveSchedule(SaveScheduleRequest request) {
        if (request == null || request.getDate() == null || request.getItems() == null)
            return OperationResult.failure(Status.INVALID, "Dữ liệu lịch chiếu không hợp lệ.");
        LocalDate date = request.getDate();
        Map<Integer, Room> roomMap = rooms.findAll().stream().collect(Collectors.toMap(Room::getRoomId, Function.identity()));
        Map<Integer, Movie> movieMap = movies.findAllById(request.getItems().stream()
                        .map(SaveScheduleItemRequest::getMovieId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Movie::getMovieId, Function.identity()));
        if (request.getItems().stream().map(SaveScheduleItemRequest::getShowtimeId)
                .filter(id -> id > 0).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .values().stream().anyMatch(count -> count > 1))
            return OperationResult.failure(Status.INVALID, "ShowtimeID bị trùng trong dữ liệu gửi lên.");

        List<Entry> incoming = new ArrayList<>();
        for (SaveScheduleItemRequest item : request.getItems()) {
            Movie movie = movieMap.get(item.getMovieId()); Room room = roomMap.get(item.getRoomId());
            if (movie == null || room == null) return OperationResult.failure(Status.INVALID, "Phim hoặc phòng chiếu không tồn tại.");
            if (isImax(room.getRoomName()) && !extractFormats(movie.getTrailer()).contains("IMAX"))
                return OperationResult.failure(Status.INVALID, "Phim chỉ khả dụng ở định dạng 2D.");
            LocalDateTime start;
            try { start = parseStart(date, item.getStartTime()); }
            catch (DateTimeParseException exception) { return OperationResult.failure(Status.INVALID, "Giờ bắt đầu không hợp lệ."); }
            LocalDateTime end = start.plusMinutes(movie.getDuration());
            if (movie.getDuration() <= 0 || start.toLocalTime().isBefore(OPENING) || end.toLocalTime().isAfter(CLOSING)
                    || !end.toLocalDate().equals(date))
                return OperationResult.failure(Status.INVALID, "Suất chiếu phải nằm trong khung giờ từ 08:00 đến 23:00.");
            incoming.add(new Entry(item.getShowtimeId(), movie, room, start, end, basePrice(room.getRoomName(), date)));
        }
        List<Showtime> old = showtimes.findByDateOrderByRoomRoomIdAscStartTimeAsc(date);
        Map<Integer, Showtime> oldById = old.stream().collect(Collectors.toMap(Showtime::getShowtimeId, Function.identity()));
        Set<Integer> ids = incoming.stream().map(Entry::id).filter(id -> id > 0).collect(Collectors.toSet());
        if (ids.stream().anyMatch(id -> !oldById.containsKey(id)))
            return OperationResult.failure(Status.INVALID, "Có ShowtimeID không tồn tại trong ngày đã chọn.");
        for (Entry entry : incoming) {
            if (entry.id() <= 0) continue;
            Showtime current = oldById.get(entry.id());
            if (tickets.countByShowtimeShowtimeId(entry.id()) > 0 &&
                    (!current.getMovie().getMovieId().equals(entry.movie().getMovieId())
                            || !current.getRoom().getRoomId().equals(entry.room().getRoomId())
                            || !current.getStartTime().equals(entry.start()) || !current.getEndTime().equals(entry.end())))
                return OperationResult.failure(Status.CONFLICT, "Suất chiếu đã có vé nên không thể thay đổi.");
        }
        List<Entry> retainedTicketed = old.stream().filter(item -> !ids.contains(item.getShowtimeId())
                        && tickets.countByShowtimeShowtimeId(item.getShowtimeId()) > 0)
                .map(item -> new Entry(item.getShowtimeId(), item.getMovie(), item.getRoom(), item.getStartTime(),
                        item.getEndTime(), item.getBasePrice())).toList();
        List<Entry> finalEntries = new ArrayList<>(retainedTicketed); finalEntries.addAll(incoming);
        for (var group : finalEntries.stream().collect(Collectors.groupingBy(item -> item.room().getRoomId())).values()) {
            List<Entry> ordered = group.stream().sorted(Comparator.comparing(Entry::start)).toList();
            for (int i = 1; i < ordered.size(); i++) if (ordered.get(i).start().isBefore(ordered.get(i - 1).end()))
                return OperationResult.failure(Status.CONFLICT, "Phòng chiếu đã có lịch bị trùng giờ.");
        }
        List<Showtime> removable = old.stream().filter(item -> !ids.contains(item.getShowtimeId())
                && tickets.countByShowtimeShowtimeId(item.getShowtimeId()) == 0).toList();
        int created = 0;
        try {
            showtimes.deleteAll(removable);
            for (Entry entry : incoming) {
                Showtime value = entry.id() > 0 ? oldById.get(entry.id()) : new Showtime();
                value.setMovie(entry.movie()); value.setRoom(entry.room()); value.setDate(date);
                value.setStartTime(entry.start()); value.setEndTime(entry.end()); value.setBasePrice(entry.price());
                showtimes.save(value); if (entry.id() <= 0) created++;
            }
            showtimes.flush();
        } catch (DataAccessException exception) {
            return OperationResult.failure(Status.FAILURE, "Không lưu được lịch chiếu vào database.");
        }
        return new OperationResult(Status.SUCCESS, "Lưu lịch chiếu thành công.", finalEntries.size(), created,
                incoming.size() - created, removable.size());
    }

    @Override @Transactional(readOnly = true)
    public StaffHallsViewModel getHalls() {
        List<Ticket> allTickets = tickets.findAllByOrderByShowtimeStartTimeDesc();
        List<StaffReservationViewModel> reservations = staffReservations(allTickets);
        StaffHallsViewModel result = new StaffHallsViewModel();
        result.setRooms(roomSummaries().stream().map(summary -> {
            List<Seat> roomSeats = seats.findByRoomRoomIdOrderBySeatCode(summary.getRoomId());
            StaffHallViewModel hall = new StaffHallViewModel();
            hall.setRoomId(summary.getRoomId()); hall.setRoomName(summary.getRoomName()); hall.setSeatCount(roomSeats.size());
            hall.setRegularCount(countType(roomSeats, "Regular")); hall.setVipCount(countType(roomSeats, "VIP"));
            hall.setCoupleCount(countType(roomSeats, "Couple"));
            hall.setOrderedCount((int) roomSeats.stream().filter(seat -> !"Not Order".equals(seat.getSeatStatus())).count());
            List<StaffReservationViewModel> roomReservations = reservations.stream()
                    .filter(item -> item.getRoomId() == summary.getRoomId()).toList();
            hall.setReservationCount(roomReservations.size()); hall.setReservations(roomReservations);
            hall.setColumnCount(columnCount(roomSeats)); hall.setRows(staffSeatRows(roomSeats, hall.getColumnCount()));
            return hall;
        }).toList());
        return result;
    }

    @Override @Transactional(readOnly = true)
    public ConcessionsViewModel getConcessions(Combo form) {
        ConcessionsViewModel result = new ConcessionsViewModel();
        result.setItems(combos.findAll().stream().sorted(Comparator.comparing(Combo::getComboName)).toList());
        result.setForm(form == null ? new Combo() : form);
        YearMonth month = YearMonth.now();
        List<Payment> monthly = payments.findAll().stream().filter(payment -> "Success".equals(payment.getStatus())
                && payment.getPaymentDate() != null && YearMonth.from(payment.getPaymentDate()).equals(month)).toList();
        BigDecimal revenue = monthly.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        result.setMonthlyRevenue(revenue); result.setTotalTransactions(monthly.size());
        result.setAverageOrderValue(monthly.isEmpty() ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(monthly.size()), 2, RoundingMode.HALF_UP));
        return result;
    }

    @Override @Transactional(readOnly = true) public Optional<Combo> getConcession(int comboId) { return combos.findById(comboId); }

    @Override @Transactional
    public OperationResult createConcession(Combo combo) {
        if (!validCombo(combo)) return OperationResult.failure(Status.INVALID, "Dữ liệu combo không hợp lệ.");
        combo.setComboId(null); combo.setComboName(combo.getComboName().trim()); combos.save(combo);
        return OperationResult.success("Thêm món thành công.");
    }

    @Override @Transactional
    public OperationResult updateConcession(Combo form) {
        if (form == null || form.getComboId() == null || !validCombo(form))
            return OperationResult.failure(Status.INVALID, "Dữ liệu combo không hợp lệ.");
        Combo combo = combos.findById(form.getComboId()).orElse(null);
        if (combo == null) return OperationResult.failure(Status.NOT_FOUND, "Không tìm thấy combo.");
        combo.setComboName(form.getComboName().trim()); combo.setComboPrice(form.getComboPrice());
        combo.setQuantity(form.getQuantity()); combos.save(combo); return OperationResult.success("Đã cập nhật món thành công.");
    }

    @Override @Transactional
    public OperationResult createMovie(StaffMovieRequest request) {
        if (!validMovie(request)) return OperationResult.failure(Status.INVALID, "Dữ liệu phim không hợp lệ.");
        Movie movie = new Movie(); applyMovie(movie, request); movies.saveAndFlush(movie); saveMovieLinks(movie.getMovieId(), request);
        return OperationResult.success("Đã thêm phim vào database.");
    }

    @Override @Transactional
    public OperationResult updateMovie(StaffMovieRequest request) {
        if (!validMovie(request) || request.getMovieId() <= 0)
            return OperationResult.failure(Status.INVALID, "Dữ liệu phim không hợp lệ.");
        Movie movie = movies.findById(request.getMovieId()).orElse(null);
        if (movie == null) return OperationResult.failure(Status.NOT_FOUND, "Không tìm thấy phim.");
        applyMovie(movie, request); movies.save(movie); saveMovieLinks(movie.getMovieId(), request);
        return OperationResult.success("Đã cập nhật phim.");
    }

    @Override @Transactional
    public OperationResult deleteMovie(int movieId) {
        Movie movie = movies.findById(movieId).orElse(null);
        if (movie == null) return OperationResult.failure(Status.NOT_FOUND, "Không tìm thấy phim.");
        List<Showtime> movieShowtimes = showtimes.findByMovieMovieId(movieId);
        if (movieShowtimes.stream().anyMatch(item -> tickets.countByShowtimeShowtimeId(item.getShowtimeId()) > 0))
            return OperationResult.failure(Status.CONFLICT, "Không thể xóa phim đã có vé đặt.");
        showtimes.deleteAll(movieShowtimes); movieGenres.deleteByMovieId(movieId); movieCasts.deleteByMovieId(movieId);
        movieDirectors.deleteByMovieId(movieId); movies.delete(movie);
        return OperationResult.success("Đã xóa phim khỏi database.");
    }

    private List<StaffRoomSummaryViewModel> roomSummaries() {
        return rooms.findAll().stream().sorted(Comparator.comparing(Room::getRoomId)).map(room -> {
            StaffRoomSummaryViewModel value = new StaffRoomSummaryViewModel(); value.setRoomId(room.getRoomId());
            value.setRoomName(room.getRoomName()); value.setSeatCount(seats.findByRoomRoomIdOrderBySeatCode(room.getRoomId()).size());
            String type = roomType(room.getRoomName()); value.setDisplayType(type);
            value.setTagCssClass("tag-" + type.toLowerCase(Locale.ROOT)); return value;
        }).toList();
    }

    private MovieViewModel mapMovie(Movie movie) {
        MovieViewModel value = new MovieViewModel(); value.setMovieId(movie.getMovieId()); value.setTitle(movie.getTitle());
        value.setDuration(movie.getDuration()); value.setPosterUrl(movie.getPosterUrl()); value.setSynopsis(movie.getSynopsis());
        value.setReleaseDate(movie.getReleaseDate()); value.setAgeRating(movie.getAgeRating());
        value.setTrailer(cleanTrailer(movie.getTrailer())); value.setAvailableFormats(extractFormats(movie.getTrailer()));
        if (movie.getLanguage() != null) { value.setLanguageId(movie.getLanguage().getLanguageId()); value.setLanguageName(movie.getLanguage().getLanguageName()); }
        if (movie.getCountry() != null) { value.setCountryId(movie.getCountry().getCountryId()); value.setCountryName(movie.getCountry().getCountryName()); }
        value.setGenre(joinGenres(movie.getMovieId()));
        value.setMovieDirector(joinPeople(movieDirectors.findByMovieId(movie.getMovieId()).stream().map(MovieDirector::getPerson).toList()));
        value.setMovieCast(joinPeople(movieCasts.findByMovieId(movie.getMovieId()).stream().map(MovieCast::getPerson).toList()));
        return value;
    }

    private void applyMovie(Movie movie, StaffMovieRequest form) {
        movie.setTitle(form.getTitle().trim()); movie.setDuration(form.getDuration());
        movie.setPosterUrl(text(form.getPosterUrl()) ? form.getPosterUrl().trim() : DEFAULT_POSTER);
        movie.setSynopsis(text(form.getSynopsis()) ? form.getSynopsis().trim() : "Chưa có tóm tắt");
        String trailer = text(form.getTrailer()) ? cleanTrailer(form.getTrailer())
                : text(form.getTrailerUrl()) ? cleanTrailer(form.getTrailerUrl()) : DEFAULT_TRAILER;
        movie.setTrailer(storedTrailer(trailer, form.getAvailableFormats()));
        movie.setReleaseDate(form.getReleaseDate() == null ? LocalDate.now() : form.getReleaseDate());
        movie.setAgeRating(text(form.getAgeRating()) ? form.getAgeRating().trim() : "T13");
        movie.setLanguage(resolveLanguage(form.getLanguage())); movie.setCountry(resolveCountry(form.getCountry()));
    }

    private void saveMovieLinks(int movieId, StaffMovieRequest form) {
        movieGenres.deleteByMovieId(movieId); movieDirectors.deleteByMovieId(movieId); movieCasts.deleteByMovieId(movieId);
        for (String name : names(form.getGenre())) {
            Genre genre = genres.findByNameIgnoreCase(name).orElseGet(() -> genres.saveAndFlush(newGenre(name)));
            MovieGenre link = new MovieGenre(); link.setMovieId(movieId); link.setGenreId(genre.getGenreId()); movieGenres.save(link);
        }
        for (String name : names(form.getDirector())) { Person person = person(name); MovieDirector link = new MovieDirector();
            link.setMovieId(movieId); link.setPersonId(person.getPersonId()); movieDirectors.save(link); }
        for (String name : names(form.getCast())) { Person person = person(name); MovieCast link = new MovieCast();
            link.setMovieId(movieId); link.setPersonId(person.getPersonId()); movieCasts.save(link); }
    }

    private List<StaffReservationViewModel> staffReservations(List<Ticket> source) {
        return source.stream().collect(Collectors.groupingBy(ticket -> ticket.getBooking().getBookingId())).values().stream().map(group -> {
            Ticket first = group.get(0); Booking booking = first.getBooking(); StaffReservationViewModel value = new StaffReservationViewModel();
            value.setBookingId(booking.getBookingId()); value.setRoomId(first.getShowtime().getRoom().getRoomId());
            value.setRoomName(first.getShowtime().getRoom().getRoomName()); value.setCustomerName(booking.getUser().getFullName());
            value.setCustomerEmail(booking.getUser().getEmail()); value.setMovieTitle(first.getShowtime().getMovie().getTitle());
            value.setBookingDate(booking.getBookingDate()); value.setShowtimeStart(first.getShowtime().getStartTime());
            value.setSeatCodes(group.stream().map(item -> item.getSeat().getSeatCode()).sorted().collect(Collectors.joining(", ")));
            value.setTicketCount(group.size()); value.setTotalAmount(booking.getTotalAmount()); value.setStatus(booking.getStatus()); return value;
        }).sorted(Comparator.comparing(StaffReservationViewModel::getShowtimeStart).reversed()).toList();
    }

    private List<ScreeningRoomStateViewModel.Reservation> screeningReservations(List<Ticket> source) {
        return source.stream().collect(Collectors.groupingBy(ticket -> ticket.getBooking().getBookingId())).values().stream().map(group -> {
            Booking booking = group.get(0).getBooking(); ScreeningRoomStateViewModel.Reservation value = new ScreeningRoomStateViewModel.Reservation();
            value.setBookingId(booking.getBookingId()); value.setCustomerName(booking.getUser().getFullName());
            value.setCustomerEmail(booking.getUser().getEmail()); value.setBookingDate(booking.getBookingDate());
            value.setStatus(booking.getStatus()); value.setSeatCodes(group.stream().map(item -> item.getSeat().getSeatCode()).sorted().toList());
            value.setTicketCount(group.size()); value.setTotalAmount(booking.getTotalAmount()); return value;
        }).sorted(Comparator.comparing(ScreeningRoomStateViewModel.Reservation::getBookingDate).reversed()).toList();
    }

    private ScreeningRoomStateViewModel.ShowtimeItem screeningShowtime(Showtime showtime) { var value = new ScreeningRoomStateViewModel.ShowtimeItem();
        value.setShowtimeId(showtime.getShowtimeId()); value.setMovieTitle(showtime.getMovie().getTitle()); value.setStartTime(showtime.getStartTime()); value.setEndTime(showtime.getEndTime()); return value; }
    private ScreeningRoomStateViewModel.SeatItem screeningSeat(Seat seat, String state) { var value = new ScreeningRoomStateViewModel.SeatItem();
        value.setSeatId(seat.getSeatId()); value.setSeatCode(seat.getSeatCode()); value.setSeatNumber(seatNumber(seat.getSeatCode()));
        value.setSeatType(seat.getSeatType()); value.setReservationState(state); value.setColumnSpan("Couple".equals(seat.getSeatType()) ? 2 : 1); return value; }
    private ScreeningRoomStateViewModel.SeatCounts screeningCounts(List<ScreeningRoomStateViewModel.SeatItem> source) { var value = new ScreeningRoomStateViewModel.SeatCounts();
        value.setTotal(source.size()); value.setRegular((int) source.stream().filter(item -> "Regular".equals(item.getSeatType())).count());
        value.setVip((int) source.stream().filter(item -> "VIP".equals(item.getSeatType())).count()); value.setCouple((int) source.stream().filter(item -> "Couple".equals(item.getSeatType())).count());
        value.setReserved((int) source.stream().filter(item -> "reserved".equals(item.getReservationState())).count()); value.setPending((int) source.stream().filter(item -> "pending".equals(item.getReservationState())).count());
        value.setAvailable(Math.max(0, value.getTotal() - value.getReserved() - value.getPending())); return value; }
    private List<ScreeningRoomStateViewModel.SeatRow> screeningRows(List<ScreeningRoomStateViewModel.SeatItem> source) {
        return source.stream().collect(Collectors.groupingBy(item -> rowLabel(item.getSeatCode()), TreeMap::new, Collectors.toList())).entrySet().stream().map(entry -> {
            var row = new ScreeningRoomStateViewModel.SeatRow(); row.setRowLabel(entry.getKey()); row.setSeats(entry.getValue().stream().sorted(Comparator.comparingInt(item -> seatNumberValue(item.getSeatCode()))).toList()); return row;
        }).toList(); }

    private List<StaffSeatRowViewModel> staffSeatRows(List<Seat> source, int columns) { return source.stream()
            .collect(Collectors.groupingBy(seat -> rowLabel(seat.getSeatCode()), TreeMap::new, Collectors.toList())).entrySet().stream().map(entry -> {
                StaffSeatRowViewModel row = new StaffSeatRowViewModel(); row.setRowLabel(entry.getKey()); List<StaffSeatViewModel> cells = new ArrayList<>();
                for (Seat seat : entry.getValue().stream().sorted(Comparator.comparingInt(item -> seatNumberValue(item.getSeatCode()))).toList()) { StaffSeatViewModel cell = new StaffSeatViewModel();
                    cell.setSeatId(seat.getSeatId()); cell.setSeatCode(seat.getSeatCode()); cell.setSeatNumber(seatNumber(seat.getSeatCode())); cell.setSeatType(seat.getSeatType());
                    cell.setSeatStatus(seat.getSeatStatus()); cell.setColumnSpan("Couple".equals(seat.getSeatType()) ? 2 : 1); cells.add(cell); }
                row.setSeats(cells); return row; }).toList(); }
    private int columnCount(List<Seat> source) { return source.stream().collect(Collectors.groupingBy(seat -> rowLabel(seat.getSeatCode()))).values().stream()
            .mapToInt(row -> row.stream().mapToInt(seat -> "Couple".equals(seat.getSeatType()) ? 2 : 1).sum()).max().orElse(0); }
    private int countType(List<Seat> source, String type) { return (int) source.stream().filter(seat -> type.equals(seat.getSeatType())).count(); }

    private boolean validCombo(Combo combo) { return combo != null && text(combo.getComboName()) && combo.getComboPrice() != null
            && combo.getComboPrice().compareTo(BigDecimal.ZERO) > 0 && combo.getQuantity() >= 0; }
    private boolean validMovie(StaffMovieRequest form) { return form != null && text(form.getTitle()) && form.getDuration() > 0; }
    private Language resolveLanguage(String name) { if (!text(name)) return null; String normalized = limit(name.trim(), 100);
        return languages.findByLanguageNameIgnoreCase(normalized).orElseGet(() -> { Language value = new Language(); value.setLanguageName(normalized); return languages.save(value); }); }
    private Country resolveCountry(String name) { if (!text(name)) return null; String normalized = limit(name.trim(), 100);
        return countries.findByCountryNameIgnoreCase(normalized).orElseGet(() -> { Country value = new Country(); value.setCountryName(normalized); return countries.save(value); }); }
    private Person person(String name) { return people.findByFullNameIgnoreCase(name).orElseGet(() -> { Person value = new Person(); value.setFullName(name); return people.saveAndFlush(value); }); }
    private Genre newGenre(String name) { Genre value = new Genre(); value.setName(name); return value; }
    private List<String> names(String value) { if (!text(value)) return List.of(); return Arrays.stream(value.split(",")).map(String::trim).filter(this::text)
            .collect(Collectors.collectingAndThen(Collectors.toMap(item -> item.toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a, LinkedHashMap::new), map -> new ArrayList<>(map.values()))); }
    private String joinGenres(int movieId) { return movieGenres.findByMovieId(movieId).stream().map(MovieGenre::getGenre).filter(Objects::nonNull).map(Genre::getName).collect(Collectors.joining(", ")); }
    private String joinPeople(List<Person> values) { return values.stream().filter(Objects::nonNull).map(Person::getFullName).collect(Collectors.joining(", ")); }
    private LocalDateTime parseStart(LocalDate date, String value) { try { return LocalDateTime.of(date, LocalTime.parse(value)); } catch (DateTimeParseException ignored) { return LocalDateTime.parse(value); } }
    private BigDecimal basePrice(String room, LocalDate date) { boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        if (isImax(room)) return BigDecimal.valueOf(weekend ? 165_000 : 135_000); return BigDecimal.valueOf(weekend ? 85_000 : 65_000); }
    private boolean isImax(String room) { return room != null && room.toUpperCase(Locale.ROOT).contains("IMAX"); }
    private String roomType(String room) { String upper = room == null ? "" : room.toUpperCase(Locale.ROOT); if (upper.contains("IMAX")) return "IMAX"; if (upper.contains("VIP")) return "VIP"; if (upper.contains("DELUXE")) return "DELUXE"; return "STANDARD"; }
    private String cleanTrailer(String stored) { if (!text(stored)) return ""; int index = stored.toUpperCase(Locale.ROOT).indexOf("[COSMOS_FORMATS:"); return (index < 0 ? stored : stored.substring(0, index)).trim(); }
    private String extractFormats(String stored) { if (!text(stored)) return "2D"; int marker = stored.toUpperCase(Locale.ROOT).indexOf("[COSMOS_FORMATS:"); if (marker < 0) return "2D";
        int start = marker + "[COSMOS_FORMATS:".length(), end = stored.indexOf(']', start); if (end <= start) return "2D"; List<String> values = Arrays.stream(stored.substring(start, end).split(","))
                .map(String::trim).map(item -> item.toUpperCase(Locale.ROOT)).filter(item -> item.equals("2D") || item.equals("IMAX")).distinct().toList(); return values.isEmpty() ? "2D" : String.join(",", values); }
    private String storedTrailer(String trailer, List<String> formats) { LinkedHashSet<String> values = new LinkedHashSet<>(); if (formats != null) formats.stream().map(item -> item.toUpperCase(Locale.ROOT))
            .filter(item -> item.equals("2D") || item.equals("IMAX")).forEach(values::add); if (values.isEmpty()) values.add("2D"); String marker = FORMAT_MARKER + String.join(",", values) + "]";
        String clean = text(trailer) ? cleanTrailer(trailer) : DEFAULT_TRAILER; return limit(clean, Math.max(0, 1000 - marker.length())) + marker; }
    private String rowLabel(String code) { String value = code == null ? "" : code.replaceAll("[^A-Za-z].*$", ""); return value.isBlank() ? "?" : value; }
    private int seatNumberValue(String code) { try { return Integer.parseInt(code == null ? "" : code.replaceAll("\\D", "")); } catch (NumberFormatException exception) { return Integer.MAX_VALUE; } }
    private String seatNumber(String code) { int number = seatNumberValue(code); return number == Integer.MAX_VALUE ? code : Integer.toString(number); }
    private boolean text(String value) { return value != null && !value.isBlank(); }
    private String limit(String value, int size) { return value.length() <= size ? value : value.substring(0, size); }
    private record Entry(int id, Movie movie, Room room, LocalDateTime start, LocalDateTime end, BigDecimal price) { }
}
