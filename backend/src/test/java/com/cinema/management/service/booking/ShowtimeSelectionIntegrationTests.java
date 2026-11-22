package com.cinema.management.service.booking;

import com.cinema.management.model.dto.request.SelectShowtimeRequest;
import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.booking.*;
import com.cinema.management.model.entity.movie.Movie;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:showtime_selection;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class ShowtimeSelectionIntegrationTests {
    @Autowired private BookingService service;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    @Autowired private WebApplicationContext context;

    @BeforeAll
    static void rowVersion(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("ALTER TABLE booking_holds ALTER COLUMN row_version SET DEFAULT SECURE_RAND(8)");
        jdbc.execute("ALTER TABLE booking_holds ALTER COLUMN row_version SET ON UPDATE SECURE_RAND(8)");
    }

    @Test
    void createsHoldWithoutHydratingMovieRoomOrShowtimeEntities() {
        Fixture f = fixture(); Statistics stats = reset();
        Instant before = Instant.now();
        var result = service.selectShowtime(f.user.getUserId(), null, request(f.movie, f.show), true);
        em.flush();
        assertEquals(BookingService.Status.SUCCESS, result.status());
        assertEquals("IMAX", result.format()); assertEquals("18:00", result.time());
        assertEquals(f.show.getDate().toString(), result.date());
        assertEquals(1, stats.getEntityInsertCount()); assertEquals(0, stats.getEntityUpdateCount());
        assertTrue(stats.getPrepareStatementCount() <= 3, "Validation + insert + optional generated-version retrieval");
        for (Class<?> type : new Class<?>[]{Movie.class, Room.class, Showtime.class}) {
            assertEquals(0, stats.getEntityStatistics(type.getName()).getLoadCount(), type.getSimpleName());
        }
        BookingHold hold = em.find(BookingHold.class, result.holdId());
        assertEquals(f.user.getUserId(), hold.getUser().getUserId());
        assertEquals(f.show.getShowtimeId(), hold.getShowtime().getShowtimeId());
        assertFalse(hold.getExpiresAtUtc().isBefore(before.plusSeconds(599)));
        assertFalse(hold.getExpiresAtUtc().isAfter(Instant.now().plusSeconds(600)));
    }

    @Test
    void sameShowtimeNeedsOnlyTwoReadsAndKeepsResourcesExpiryAndVersion() {
        Fixture f = fixture(); resources(f); em.flush(); em.clear();
        BookingHold persisted = em.find(BookingHold.class, f.hold.getBookingHoldId());
        Instant expiry = persisted.getExpiresAtUtc(); byte[] version = persisted.getRowVersion().clone();
        Statistics stats = reset();
        var result = service.selectShowtime(f.user.getUserId(), f.hold.getBookingHoldId(), request(f.movie, f.show), true);
        em.flush();
        assertEquals(BookingService.Status.SUCCESS, result.status()); assertEquals(f.hold.getBookingHoldId(), result.holdId());
        assertEquals(2, stats.getPrepareStatementCount());
        assertEquals(0, stats.getEntityInsertCount()); assertEquals(0, stats.getEntityUpdateCount());
        assertEquals(0, stats.getEntityDeleteCount());
        persisted = em.find(BookingHold.class, result.holdId());
        assertEquals(expiry, persisted.getExpiresAtUtc()); assertArrayEquals(version, persisted.getRowVersion());
        var checkout = service.getCheckoutView(result.holdId()).orElseThrow();
        assertEquals(1, checkout.getSelectedSeats().size()); assertEquals(1, checkout.getConcessions().size());
        assertEquals(new BigDecimal("120000.00"), checkout.getGrandTotal());
    }

    @Test
    void preparedHoldWithoutShowtimeIsUpdatedInPlaceAndKeepsItsExpiry() {
        Fixture f = fixture(); f.hold.setShowtime(null);
        long expiry = f.hold.getExpiresAtUtc().toEpochMilli(); Statistics stats = reset();
        var result = service.selectShowtime(f.user.getUserId(), f.hold.getBookingHoldId(), request(f.movie, f.show), false);
        em.flush();
        assertEquals(BookingService.Status.SUCCESS, result.status()); assertEquals(f.hold.getBookingHoldId(), result.holdId());
        assertEquals(0, stats.getEntityInsertCount()); assertEquals(1, stats.getEntityUpdateCount());
        assertTrue(stats.getPrepareStatementCount() <= 4, "Two reads plus update/generated version");
        assertEquals(0, stats.getEntityStatistics(Showtime.class.getName()).getLoadCount());
        em.clear(); BookingHold hold = em.find(BookingHold.class, result.holdId());
        assertEquals(f.show.getShowtimeId(), hold.getShowtime().getShowtimeId());
        assertEquals(expiry, hold.getExpiresAtUtc().toEpochMilli());
    }

    @Test
    void changingShowtimeReturnsOldSeatAndComboReservationsWithoutRenewingExpiry() {
        Fixture f = fixture(); resources(f);
        Showtime next = showtime(f.movie, f.show.getRoom(), 20);
        long expiry = f.hold.getExpiresAtUtc().toEpochMilli(); reset();
        var result = service.selectShowtime(f.user.getUserId(), f.hold.getBookingHoldId(), request(f.movie, next), true);
        em.flush(); em.clear();
        assertEquals(BookingService.Status.SUCCESS, result.status()); assertEquals(f.hold.getBookingHoldId(), result.holdId());
        BookingHold hold = em.find(BookingHold.class, result.holdId());
        assertEquals(next.getShowtimeId(), hold.getShowtime().getShowtimeId());
        assertEquals(expiry, hold.getExpiresAtUtc().toEpochMilli());
        assertEquals(0L, em.createQuery("select count(h) from BookingSeatHold h", Long.class).getSingleResult());
        assertEquals(0L, em.createQuery("select count(h) from BookingComboHold h", Long.class).getSingleResult());
        assertEquals(0, em.createQuery("select c from Combo c", Combo.class).getSingleResult().getReservedQuantity());
    }

    @Test
    void expiredHoldIsReleasedAndReplacedWithANewTenMinuteHold() {
        Fixture f = fixture(); resources(f); f.hold.setExpiresAtUtc(Instant.now().minusSeconds(10)); reset();
        var result = service.selectShowtime(f.user.getUserId(), f.hold.getBookingHoldId(), request(f.movie, f.show), true);
        em.flush(); em.clear();
        assertEquals(BookingService.Status.SUCCESS, result.status()); assertNotEquals(f.hold.getBookingHoldId(), result.holdId());
        assertEquals("Expired", em.find(BookingHold.class, f.hold.getBookingHoldId()).getStatus());
        assertEquals("Pending", em.find(BookingHold.class, result.holdId()).getStatus());
        assertEquals(0L, em.createQuery("select count(h) from BookingSeatHold h", Long.class).getSingleResult());
        assertEquals(0, em.createQuery("select c from Combo c", Combo.class).getSingleResult().getReservedQuantity());
    }

    @Test
    void changingMovieCancelsTheOldHoldAndCreatesTheCorrectNewHold() {
        Fixture f = fixture(); resources(f);
        Movie other = new Movie(); other.setTitle("Other selection movie"); other.setPosterUrl("/other.jpg");
        other.setTrailer("https://youtu.be/other"); em.persist(other);
        Showtime show = showtime(other, f.show.getRoom(), 21); reset();
        var result = service.selectShowtime(f.user.getUserId(), f.hold.getBookingHoldId(), request(other, show), true);
        em.flush(); em.clear();
        assertEquals(BookingService.Status.SUCCESS, result.status()); assertNotEquals(f.hold.getBookingHoldId(), result.holdId());
        assertEquals("Cancelled", em.find(BookingHold.class, f.hold.getBookingHoldId()).getStatus());
        BookingHold next = em.find(BookingHold.class, result.holdId());
        assertEquals(other.getMovieId(), next.getMovie().getMovieId());
        assertEquals(show.getShowtimeId(), next.getShowtime().getShowtimeId());
        assertEquals(0L, em.createQuery("select count(h) from BookingSeatHold h", Long.class).getSingleResult());
        assertEquals(0, em.createQuery("select c from Combo c", Combo.class).getSingleResult().getReservedQuantity());
    }

    @Test
    void pendingPaymentStillRedirectsToItsOriginalReference() {
        Fixture f = fixture(); resources(f); reset();
        var payment = service.startPayment(f.user.getUserId(), f.hold.getBookingHoldId());
        assertEquals(BookingService.Status.SUCCESS, payment.status()); reset();
        var result = service.selectShowtime(f.user.getUserId(), f.hold.getBookingHoldId(), request(f.movie, f.show), true);
        assertEquals(BookingService.Status.PAYMENT_PENDING, result.status());
        assertEquals(payment.paymentReference(), result.paymentReference());
        assertEquals("PaymentPending", em.find(BookingHold.class, f.hold.getBookingHoldId()).getStatus());
        assertEquals(1L, em.createQuery("select count(h) from BookingSeatHold h", Long.class).getSingleResult());
    }

    @Test
    void invalidMissingPastOrWrongMovieShowtimesCannotMutateTheHold() {
        Fixture f = fixture();
        Showtime past = showtime(f.movie, f.show.getRoom(), 10);
        past.setDate(LocalDate.now().minusDays(1)); past.setStartTime(past.getDate().atTime(10, 0));
        past.setEndTime(past.getStartTime().plusHours(2)); reset();
        SelectShowtimeRequest wrongMovie = request(f.movie, f.show); wrongMovie.setMovieId(Integer.MAX_VALUE);
        SelectShowtimeRequest missing = request(f.movie, f.show); missing.setShowtimeId(Integer.MAX_VALUE);
        for (var form : new SelectShowtimeRequest[]{wrongMovie, missing, request(f.movie, past), new SelectShowtimeRequest()}) {
            var result = service.selectShowtime(f.user.getUserId(), f.hold.getBookingHoldId(), form, true);
            assertEquals(BookingService.Status.INVALID, result.status());
        }
        em.flush(); assertEquals(0, emf.unwrap(SessionFactory.class).getStatistics().getEntityUpdateCount());
        assertEquals("Pending", em.find(BookingHold.class, f.hold.getBookingHoldId()).getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/booking/select-showtime-from-schedule", "/booking/select-showtime"})
    void postKeepsSessionAndRedirectToThreeQuerySeatPage(String path) throws Exception {
        Fixture f = fixture(); reset();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var response = mvc.perform(post(path).with(csrf()).with(user(f.user.getEmail()).roles("KhachHang"))
                        .sessionAttr("UserID", f.user.getUserId())
                        .param("movieId", f.movie.getMovieId().toString()).param("showtimeId", f.show.getShowtimeId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/booking/select-seats")).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) response.getRequest().getSession(false);
        assertNotNull(session); assertNotNull(session.getAttribute("BookingHoldId"));
        assertEquals(f.show.getShowtimeId(), session.getAttribute("SelectedShowtimeId"));
        assertEquals("IMAX", session.getAttribute("SelectedFormat"));
        Statistics stats = reset();
        mvc.perform(get("/booking/select-seats").session(session).with(user(f.user.getEmail()).roles("KhachHang")))
                .andExpect(status().isOk()).andExpect(view().name("booking/select-seats"));
        assertEquals(3, stats.getPrepareStatementCount());
    }

    private Fixture fixture() {
        User user = new User(); user.setFullName("Selection tester"); user.setEmail("selection@example.com");
        user.setRole("KhachHang"); em.persist(user);
        Movie movie = new Movie(); movie.setTitle("Selection movie"); movie.setPosterUrl("/poster.jpg");
        movie.setTrailer("https://youtu.be/test"); em.persist(movie);
        Room room = new Room(); room.setRoomName("IMAX Hall"); em.persist(room);
        Showtime show = showtime(movie, room, 18);
        BookingHold hold = new BookingHold(); hold.setBookingHoldId(UUID.randomUUID()); hold.setMovie(movie);
        hold.setShowtime(show); hold.setUser(user); hold.setCreatedAtUtc(Instant.now());
        hold.setExpiresAtUtc(Instant.now().plusSeconds(600)); em.persist(hold);
        return new Fixture(user, movie, show, hold);
    }

    private void resources(Fixture f) {
        SeatTypePricing price = new SeatTypePricing(); price.setSeatType("Regular"); em.persist(price);
        Seat seat = new Seat(); seat.setRoom(f.show.getRoom()); seat.setSeatCode("A1"); seat.setSeatType("Regular"); em.persist(seat);
        BookingSeatHold held = new BookingSeatHold(); held.setBookingHoldId(f.hold.getBookingHoldId());
        held.setShowtimeId(f.show.getShowtimeId()); held.setSeatId(seat.getSeatId());
        held.setUnitPrice(new BigDecimal("60000")); held.setHeldAtUtc(Instant.now()); em.persist(held);
        Combo combo = new Combo(); combo.setComboName("Popcorn"); combo.setQuantity(10); combo.setReservedQuantity(2);
        combo.setComboPrice(new BigDecimal("35000")); em.persist(combo);
        BookingComboHold own = new BookingComboHold(); own.setBookingHoldId(f.hold.getBookingHoldId());
        own.setComboId(combo.getComboId()); own.setQuantity(2); own.setUnitPrice(new BigDecimal("30000")); em.persist(own);
    }

    private Showtime showtime(Movie movie, Room room, int hour) {
        Showtime show = new Showtime(); show.setMovie(movie); show.setRoom(room); show.setDate(LocalDate.now().plusDays(1));
        show.setStartTime(show.getDate().atTime(hour, 0)); show.setEndTime(show.getStartTime().plusHours(2));
        show.setBasePrice(new BigDecimal("65000")); em.persist(show); return show;
    }

    private SelectShowtimeRequest request(Movie movie, Showtime show) {
        SelectShowtimeRequest form = new SelectShowtimeRequest(); form.setMovieId(movie.getMovieId());
        form.setShowtimeId(show.getShowtimeId()); form.setFormat("untrusted client format"); return form;
    }

    private Statistics reset() {
        em.flush(); em.clear(); Statistics stats = emf.unwrap(SessionFactory.class).getStatistics(); stats.clear(); return stats;
    }
    private record Fixture(User user, Movie movie, Showtime show, BookingHold hold) { }
}
