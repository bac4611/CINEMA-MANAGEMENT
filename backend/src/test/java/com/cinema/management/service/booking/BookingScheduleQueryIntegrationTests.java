package com.cinema.management.service.booking;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.booking.*;
import com.cinema.management.model.entity.movie.*;
import com.cinema.management.model.entity.theater.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking_schedule_queries;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class BookingScheduleQueryIntegrationTests {
    @Autowired private BookingService service;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    @Autowired private WebApplicationContext context;

    @BeforeAll
    static void rowVersion(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("ALTER TABLE booking_holds ALTER COLUMN row_version SET DEFAULT SECURE_RAND(8)");
        jdbc.execute("ALTER TABLE booking_holds ALTER COLUMN row_version SET ON UPDATE SECURE_RAND(8)");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 25})
    void fiveQueriesRegardlessOfMoviesRoomsAndShowtimes(int extras) {
        fixture();
        for (int i = 0; i < extras; i++) {
            Movie movie = movie("Extra " + i);
            Room room = room("Extra Hall " + i, 2);
            showtime(movie, room, LocalDate.now().plusDays(2), 12);
            showtime(movie, room, LocalDate.now().plusDays(3), 12);
        }
        Statistics stats = reset();
        var schedule = service.getSchedule(null, null);
        assertEquals(2 + extras, schedule.getMovies().size());
        assertEquals(5, stats.getPrepareStatementCount());
        for (Class<?> entity : List.of(Seat.class, Ticket.class, BookingHold.class, Movie.class, Showtime.class)) {
            assertEquals(0, stats.getEntityStatistics(entity.getName()).getLoadCount(),
                    "Use scalar rows and aggregates, not " + entity.getSimpleName() + " entities");
        }
    }

    @Test
    void preservesWindowMetadataPricesFormatsAndAvailabilityRules() {
        Fixture f = fixture();
        LocalDate selected = LocalDate.now().plusDays(5);
        Statistics stats = reset();
        var schedule = service.getSchedule(null, selected);
        assertEquals(selected, schedule.getSelectedDate());
        assertEquals(14, schedule.getDateOptions().size());
        assertEquals(LocalDate.now().plusDays(13), schedule.getDateOptions().getLast().getDate());
        assertEquals(List.of("Alpha", "Zulu"), schedule.getMovies().stream().map(m -> m.getTitle()).toList());
        var alpha = schedule.getMovies().getFirst();
        assertEquals(f.movie.getMovieId(), alpha.getMovieId());
        assertEquals("Action, Drama", alpha.getGenres());
        assertEquals("/poster.jpg", alpha.getPosterUrl());
        assertEquals(120, alpha.getDurationMinutes());
        assertEquals("T16", alpha.getAgeRating());
        assertEquals(List.of("2D LỒNG TIẾNG", "IMAX 2D LỒNG TIẾNG"), alpha.getFormats());
        assertEquals(3, alpha.getShowtimes().size(), "Exclude past and outside the fourteen-day window");
        var first = alpha.getShowtimes().getFirst();
        assertEquals(30, first.getTotalSeats());
        assertEquals(26, first.getRemainingSeats(), "One sold plus three active hold statuses; expired/cancelled excluded");
        assertEquals("26 ghế trống", first.getAvailabilityLabel());
        assertFalse(first.isLowAvailability()); assertFalse(first.isLate()); assertFalse(first.isSoldOut());
        var second = alpha.getShowtimes().get(1);
        assertEquals(30, second.getRemainingSeats(), "Counts must not leak between showtimes sharing a room");
        assertTrue(second.isLate());
        var emptyRoom = alpha.getShowtimes().getLast();
        assertEquals(0, emptyRoom.getTotalSeats()); assertTrue(emptyRoom.isSoldOut());
        assertEquals("Hết vé", emptyRoom.getAvailabilityLabel());
        var zulu = schedule.getMovies().getLast().getShowtimes().getFirst();
        assertTrue(zulu.isLowAvailability()); assertEquals("Còn ít ghế", zulu.getAvailabilityLabel());
        assertEquals("2D PHỤ ĐỀ", zulu.getFormat());
        assertEquals(5, stats.getPrepareStatementCount());
    }

    @Test
    void filteredMovieUsesFiveQueriesAndKeepsItsTitleWithoutShowtimes() {
        Fixture f = fixture();
        Statistics stats = reset();
        var schedule = service.getSchedule(f.movie.getMovieId(), null);
        assertEquals("Alpha", schedule.getMovieFilterTitle()); assertEquals(1, schedule.getMovies().size());
        assertEquals(5, stats.getPrepareStatementCount());
        Movie noSchedule = movie("No schedule");
        stats = reset();
        var empty = service.getSchedule(noSchedule.getMovieId(), null);
        assertEquals("No schedule", empty.getMovieFilterTitle()); assertTrue(empty.getMovies().isEmpty());
        assertEquals(14, empty.getDateOptions().size()); assertEquals(1, stats.getPrepareStatementCount());
        stats = reset();
        assertNull(service.getSchedule(Integer.MAX_VALUE, null));
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void emptyScheduleStopsAfterOneQuery() {
        Statistics stats = reset();
        var schedule = service.getSchedule(null, null);
        assertTrue(schedule.getMovies().isEmpty()); assertEquals(14, schedule.getDateOptions().size());
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void rendersActualScheduleWithoutAdditionalSql() throws Exception {
        fixture();
        Statistics stats = reset();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String html = mvc.perform(get("/booking")).andExpect(status().isOk())
                .andExpect(view().name("booking/index")).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String value : List.of("Alpha", "Zulu", "Action, Drama", "bookingScheduleForm", "name=\"_csrf\"",
                "cosmos-site-footer", "data-showtime-button", "26 ghế trống", "</html>")) assertTrue(html.contains(value), value);
        assertEquals(5, stats.getPrepareStatementCount());
    }

    private Fixture fixture() {
        Movie movie = movie("Alpha");
        Language language = new Language(); language.setLanguageName("Tiếng Việt"); em.persist(language);
        movie.setLanguage(language);
        for (String name : List.of("Action", "Drama")) {
            Genre genre = new Genre(); genre.setName(name); em.persist(genre);
            MovieGenre link = new MovieGenre(); link.setMovieId(movie.getMovieId()); link.setGenreId(genre.getGenreId());
            em.persist(link);
        }
        Room room = room("Standard", 30);
        Showtime first = showtime(movie, room, LocalDate.now().plusDays(1), 18);
        showtime(movie, room, LocalDate.now().plusDays(2), 22);
        showtime(movie, room("IMAX", 0), LocalDate.now().plusDays(13), 22);
        showtime(movie, room, LocalDate.now().minusDays(1), 18);
        showtime(movie, room, LocalDate.now().plusDays(14), 18);
        if (LocalTime.now().isAfter(LocalTime.of(0, 1))) showtime(movie, room, LocalDate.now(), 0);
        Movie zulu = movie("Zulu"); showtime(zulu, room("Small", 2), LocalDate.now().plusDays(2), 18);
        User user = new User(); user.setFullName("Schedule tester"); user.setEmail("schedule@example.com"); em.persist(user);
        Booking booking = new Booking(); booking.setUser(user); booking.setBookingDate(LocalDateTime.now()); em.persist(booking);
        List<Seat> seats = em.createQuery("select s from Seat s where s.room=:room order by s.seatId", Seat.class)
                .setParameter("room", room).getResultList();
        Ticket ticket = new Ticket(); ticket.setBooking(booking); ticket.setShowtime(first);
        ticket.setSeat(seats.getFirst()); ticket.setTicketCode("SCHEDULE-TICKET"); em.persist(ticket);
        for (int i = 1; i <= 5; i++) {
            BookingHold hold = new BookingHold(); hold.setBookingHoldId(UUID.randomUUID()); hold.setMovie(movie);
            hold.setShowtime(first); hold.setUser(user); hold.setCreatedAtUtc(Instant.now());
            hold.setStatus(List.of("Pending", "PaymentPending", "Processing", "Pending", "Cancelled").get(i-1));
            hold.setExpiresAtUtc(Instant.now().plusSeconds(i == 4 ? -60 : 600)); em.persist(hold);
            BookingSeatHold held = new BookingSeatHold(); held.setBookingHoldId(hold.getBookingHoldId());
            held.setShowtimeId(first.getShowtimeId()); held.setSeatId(seats.get(i).getSeatId());
            held.setHeldAtUtc(Instant.now()); em.persist(held);
        }
        return new Fixture(movie);
    }

    private Movie movie(String title) {
        Movie movie = new Movie(); movie.setTitle(title); movie.setPosterUrl("/poster.jpg");
        movie.setTrailer("https://youtu.be/test"); movie.setDuration((short)120); movie.setAgeRating("T16");
        em.persist(movie); return movie;
    }

    private Room room(String name, int seatCount) {
        Room room = new Room(); room.setRoomName(name); em.persist(room);
        SeatTypePricing price = em.find(SeatTypePricing.class, "Regular");
        if (price == null) { price = new SeatTypePricing(); price.setSeatType("Regular"); em.persist(price); }
        for (int i=1; i<=seatCount; i++) {
            Seat seat = new Seat(); seat.setRoom(room); seat.setSeatCode("A"+i); seat.setSeatType("Regular"); em.persist(seat);
        }
        return room;
    }

    private Showtime showtime(Movie movie, Room room, LocalDate date, int hour) {
        Showtime show = new Showtime(); show.setMovie(movie); show.setRoom(room); show.setDate(date);
        show.setStartTime(date.atTime(hour, 0)); show.setEndTime(show.getStartTime().plusHours(2));
        show.setBasePrice(new BigDecimal("65000")); em.persist(show); return show;
    }

    private Statistics reset() {
        em.flush(); em.clear();
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics(); stats.clear(); return stats;
    }

    private record Fixture(Movie movie) { }
}
