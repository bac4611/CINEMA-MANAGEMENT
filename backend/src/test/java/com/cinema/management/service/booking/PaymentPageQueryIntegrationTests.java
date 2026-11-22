package com.cinema.management.service.booking;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.booking.*;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.payment.PaymentIntent;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_page_queries;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "sepay.bank-account-number=123456789", "sepay.bank-code=TESTBANK"
})
@Transactional
class PaymentPageQueryIntegrationTests {
    @Autowired private BookingService service;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    @Autowired private WebApplicationContext context;

    @BeforeAll
    static void emulateRowVersion(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("ALTER TABLE booking_holds ALTER COLUMN row_version SET DEFAULT SECURE_RAND(8)");
        jdbc.execute("ALTER TABLE booking_holds ALTER COLUMN row_version SET ON UPDATE SECURE_RAND(8)");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void paymentPageUsesThreeQueriesWithMoreSeatsAndCombos(int items) {
        Fixture f = fixture(items);
        Statistics stats = reset();
        var result = service.getPaymentPage(f.user.getUserId(), f.intent.getPaymentReference());
        assertEquals(BookingService.Status.SUCCESS, result.status());
        var model = result.model();
        assertEquals(items, model.getSelectedSeats().size()); assertEquals(items, model.getConcessions().size());
        assertEquals("Payment movie", model.getMovieTitle()); assertEquals("IMAX", model.getCinemaFormat());
        assertEquals("18:00", model.getSelectedTime());
        assertEquals(f.showtime.getDate().toString(), model.getSelectedDate());
        assertEquals(new BigDecimal("60000.00").multiply(BigDecimal.valueOf(items)), model.getTicketSubtotal());
        assertEquals(new BigDecimal("60000.00").multiply(BigDecimal.valueOf(items)), model.getConcessionSubtotal());
        assertEquals(new BigDecimal("999999.00"), model.getTotalAmount(), "QR uses the persisted expected amount");
        for (var combo : model.getConcessions()) {
            assertEquals(2, combo.getSelectedQuantity()); assertEquals(new BigDecimal("30000.00"), combo.getPrice());
        }
        assertEquals(f.intent.getExpiresAtUtc().getEpochSecond(), model.getExpiresAtUtc().getEpochSecond());
        assertTrue(model.getQrImageUrl().contains("amount=999999"));
        assertTrue(model.getQrImageUrl().contains("des=" + f.intent.getPaymentReference()));
        assertTrue(model.getQrImageUrl().contains("acc=123456789"));
        assertFalse(model.isShowDevelopmentPaymentButton());
        for (Class<?> type : List.of(Seat.class, Combo.class, BookingSeatHold.class, BookingComboHold.class, Ticket.class)) {
            assertEquals(0, stats.getEntityStatistics(type.getName()).getLoadCount(), type.getSimpleName());
        }
        assertEquals(3, stats.getPrepareStatementCount(), "No per-seat/per-combo SQL or lazy model reads");
        assertEquals(0, stats.getEntityUpdateCount()); assertEquals(0, stats.getEntityInsertCount());
    }

    @Test
    void noCombosStillUsesThreeQueriesAndKeepsZeroSubtotal() {
        Fixture f = fixture(1);
        em.createQuery("delete from BookingComboHold h where h.bookingHoldId=:id")
                .setParameter("id", f.hold.getBookingHoldId()).executeUpdate();
        Statistics stats = reset();
        var model = service.getPaymentPage(f.user.getUserId(), f.intent.getPaymentReference()).model();
        assertTrue(model.getConcessions().isEmpty()); assertEquals(BigDecimal.ZERO, model.getConcessionSubtotal());
        assertEquals(3, stats.getPrepareStatementCount());
    }

    @Test
    void missingReferenceOrDifferentOwnerStopsAfterOneQuery() {
        Fixture f = fixture(1);
        Statistics stats = reset();
        assertEquals(BookingService.Status.NOT_FOUND,
                service.getPaymentPage(f.user.getUserId() + 1000, f.intent.getPaymentReference()).status());
        assertEquals(1, stats.getPrepareStatementCount());
        stats = reset();
        assertEquals(BookingService.Status.NOT_FOUND, service.getPaymentPage(f.user.getUserId(), "missing").status());
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void completedPaymentRedirectsBeforeExpiryChecksWithoutLoadingOrderLines() {
        Fixture f = fixture(1);
        Booking booking = new Booking(); booking.setUser(f.user); booking.setBookingDate(LocalDateTime.now());
        booking.setStatus("Confirmed"); em.persist(booking);
        f.intent.setStatus("Success"); f.intent.setBooking(booking); f.intent.setExpiresAtUtc(Instant.now().minusSeconds(10));
        Statistics stats = reset();
        var result = service.getPaymentPage(f.user.getUserId(), f.intent.getPaymentReference());
        assertEquals(BookingService.Status.SUCCESS, result.status()); assertEquals(booking.getBookingId(), result.bookingId());
        assertNull(result.model()); assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void missingOrInvalidHoldKeepsExistingFailureResult() {
        Fixture f = fixture(1); f.hold.setStatus("Pending");
        Statistics stats = reset();
        assertEquals(BookingService.Status.INVALID, service.getPaymentPage(f.user.getUserId(), f.intent.getPaymentReference()).status());
        assertEquals(1, stats.getPrepareStatementCount());
        PaymentIntent intent = em.find(PaymentIntent.class, f.intent.getPaymentIntentId()); intent.setBookingHold(null);
        stats = reset();
        assertEquals(BookingService.Status.INVALID, service.getPaymentPage(f.user.getUserId(), f.intent.getPaymentReference()).status());
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void expiredPaymentStillReleasesSeatsAndComboReservations() {
        Fixture f = fixture(1); f.intent.setExpiresAtUtc(Instant.now().minusSeconds(10)); reset();
        assertEquals(BookingService.Status.EXPIRED,
                service.getPaymentPage(f.user.getUserId(), f.intent.getPaymentReference()).status());
        em.flush(); em.clear();
        assertEquals("Expired", em.find(PaymentIntent.class, f.intent.getPaymentIntentId()).getStatus());
        assertEquals("Expired", em.find(BookingHold.class, f.hold.getBookingHoldId()).getStatus());
        assertEquals(0L, em.createQuery("select count(h) from BookingSeatHold h", Long.class).getSingleResult());
        assertEquals(0L, em.createQuery("select count(h) from BookingComboHold h", Long.class).getSingleResult());
        assertEquals(0, em.createQuery("select c from Combo c", Combo.class).getSingleResult().getReservedQuantity());
    }

    @Test
    void actualQrPageRendersWithThreeQueriesAndExistingAuthentication() throws Exception {
        Fixture f = fixture(2); Statistics stats = reset();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String html = mvc.perform(get("/booking/payment").with(user(f.user.getEmail()).roles("KhachHang"))
                        .sessionAttr("UserID", f.user.getUserId()).sessionAttr("PaymentReference", f.intent.getPaymentReference()))
                .andExpect(status().isOk()).andExpect(view().name("booking/payment"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String expected : List.of("Payment movie", "A1", "A2", "999.999", f.intent.getPaymentReference(),
                "amount=999999", "/booking/payment-status", "/booking/payment-success", "cosmos-site-footer", "</html>")) {
            assertTrue(html.replace("\\/", "/").contains(expected), expected);
        }
        assertFalse(html.contains("action=\"/booking/complete-dev-payment\""));
        assertEquals(3, stats.getPrepareStatementCount(), "Authenticated rendering must not add lazy SQL");
    }

    private Fixture fixture(int items) {
        User user = new User(); user.setFullName("Payment tester"); user.setEmail("payment@example.com");
        user.setRole("KhachHang"); em.persist(user);
        Movie movie = new Movie(); movie.setTitle("Payment movie"); movie.setPosterUrl("/poster.jpg");
        movie.setTrailer("https://youtu.be/test"); em.persist(movie);
        Room room = new Room(); room.setRoomName("IMAX Hall"); em.persist(room);
        Showtime show = new Showtime(); show.setMovie(movie); show.setRoom(room); show.setDate(LocalDate.now().plusDays(1));
        show.setStartTime(show.getDate().atTime(18,0)); show.setEndTime(show.getStartTime().plusHours(2));
        show.setBasePrice(new BigDecimal("90000")); em.persist(show);
        SeatTypePricing pricing = new SeatTypePricing(); pricing.setSeatType("Regular"); em.persist(pricing);
        BookingHold hold = new BookingHold(); hold.setBookingHoldId(UUID.randomUUID()); hold.setMovie(movie);
        hold.setShowtime(show); hold.setUser(user); hold.setStatus("PaymentPending"); hold.setCreatedAtUtc(Instant.now());
        hold.setExpiresAtUtc(Instant.now().plusSeconds(600)); em.persist(hold);
        for (int i=1; i<=items; i++) {
            Seat seat = new Seat(); seat.setRoom(room); seat.setSeatCode("A"+i); seat.setSeatType("Regular"); em.persist(seat);
            BookingSeatHold held = new BookingSeatHold(); held.setBookingHoldId(hold.getBookingHoldId());
            held.setShowtimeId(show.getShowtimeId()); held.setSeatId(seat.getSeatId()); held.setHeldAtUtc(Instant.now());
            held.setUnitPrice(new BigDecimal("60000")); em.persist(held);
            Combo combo = new Combo(); combo.setComboName("Popcorn " + i); combo.setQuantity(10);
            combo.setReservedQuantity(2); combo.setComboPrice(new BigDecimal("50000")); em.persist(combo);
            BookingComboHold own = new BookingComboHold(); own.setBookingHoldId(hold.getBookingHoldId());
            own.setComboId(combo.getComboId()); own.setQuantity(2); own.setUnitPrice(new BigDecimal("30000")); em.persist(own);
        }
        PaymentIntent intent = new PaymentIntent(); intent.setUser(user); intent.setMovie(movie); intent.setShowtime(show);
        intent.setBookingHold(hold); intent.setPaymentReference("COSMOS20261122000000123456");
        intent.setExpectedAmount(new BigDecimal("999999")); intent.setSelectedSeatCodes("STALE-SNAPSHOT");
        intent.setExpiresAtUtc(hold.getExpiresAtUtc()); em.persist(intent);
        return new Fixture(user, show, hold, intent);
    }

    private Statistics reset() {
        em.flush(); em.clear(); Statistics stats = emf.unwrap(SessionFactory.class).getStatistics(); stats.clear(); return stats;
    }
    private record Fixture(User user, Showtime showtime, BookingHold hold, PaymentIntent intent) { }
}
