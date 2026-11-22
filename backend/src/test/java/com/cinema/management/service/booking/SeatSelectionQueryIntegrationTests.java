package com.cinema.management.service.booking;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.booking.*;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.theater.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seat_selection_queries;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class SeatSelectionQueryIntegrationTests {
    @Autowired private BookingService service;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    @Autowired private WebApplicationContext context;
    @Autowired private PlatformTransactionManager transactions;

    @BeforeAll
    static void emulateSqlServerRowVersionInH2(@Autowired JdbcTemplate jdbc) {
        // H2 does not implement SQL Server's auto-generated ROWVERSION column.
        // These changes apply only to this isolated, in-memory test database.
        jdbc.execute("ALTER TABLE booking_holds ALTER COLUMN row_version SET DEFAULT SECURE_RAND(8)");
        jdbc.execute("ALTER TABLE booking_holds ALTER COLUMN row_version SET ON UPDATE SECURE_RAND(8)");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousRequestsForSameSeatHaveOnlyOneWinner() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        UUID[] ids = new UUID[2];
        int seatId = tx.execute(status -> {
            Fixture f = fixture();
            ids[0] = f.hold.getBookingHoldId();
            ids[1] = hold(f, "Pending", 600).getBookingHoldId();
            return seat(f.room, "B1").getSeatId();
        });
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<BookingService.SeatHoldResult>> attempts = new ArrayList<>();
            for (UUID id : ids) attempts.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
                return service.updateSeat(id, seatId, true);
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<BookingService.Status> results = new ArrayList<>();
            for (int i = 0; i < attempts.size(); i++) {
                try {
                    results.add(attempts.get(i).get(20, TimeUnit.SECONDS).status());
                } catch (ExecutionException exception) {
                    // H2's serializable snapshot can reject the losing transaction
                    // at the unique key instead of observing the newly held seat.
                    // Neither a rollback nor a conflict may create a second hold.
                    Throwable cause = exception.getCause();
                    assertTrue(cause instanceof DataIntegrityViolationException
                            || cause instanceof ConcurrencyFailureException, cause.toString());
                    assertEquals(BookingService.Status.CONFLICT, service.updateSeat(ids[i], seatId, true).status(),
                            "A fresh transaction must see the winner's seat hold");
                    results.add(BookingService.Status.CONFLICT);
                }
            }
            assertEquals(1, Collections.frequency(results, BookingService.Status.SUCCESS));
            assertEquals(1, Collections.frequency(results, BookingService.Status.CONFLICT));
            tx.executeWithoutResult(status -> assertEquals(1L,
                    em.createQuery("select count(h) from BookingSeatHold h where h.seatId=:id", Long.class)
                            .setParameter("id", seatId).getSingleResult()));
        } finally {
            tx.executeWithoutResult(status -> {
                // Keep the committed concurrency fixture out of other tests.
                em.createQuery("delete from BookingComboHold").executeUpdate();
                em.createQuery("delete from Combo").executeUpdate();
                em.createQuery("delete from BookingSeatHold").executeUpdate();
                em.createQuery("delete from Ticket").executeUpdate();
                em.createQuery("delete from BookingHold").executeUpdate();
                em.createQuery("delete from Booking").executeUpdate();
                em.createQuery("delete from Seat").executeUpdate();
                em.createQuery("delete from Showtime").executeUpdate();
                em.createQuery("delete from Room").executeUpdate();
                em.createQuery("delete from Movie").executeUpdate();
                em.createQuery("delete from User").executeUpdate();
                em.createQuery("delete from SeatTypePricing").executeUpdate();
            });
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 40})
    void threeQueriesRegardlessOfUnrelatedTicketsAndPreservesSeatStates(int unrelatedTickets) {
        Fixture f = fixture();
        Showtime other = showtime(f.movie, f.room);
        for (int i = 0; i < unrelatedTickets; i++) ticket(f.booking, other, seat(f.room, "X" + i));
        Statistics stats = reset();

        var model = service.getSeatSelectionView(f.hold.getBookingHoldId()).orElseThrow();

        assertEquals(3, stats.getPrepareStatementCount());
        assertEquals(List.of("A1"), model.getSelectedSeats());
        assertEquals(Set.of("A2", "A3", "A6", "A7"), new HashSet<>(model.getOccupiedSeats()));
        assertEquals(new BigDecimal("60000.00"), model.seatPrice("A1"), "Preserve the held price");
        assertEquals(0, new BigDecimal("65000").compareTo(model.seatPrice("A4")));
        assertEquals(new BigDecimal("60000.00"), model.getTicketSubtotal());
        assertEquals("Seat query movie", model.getMovieTitle());
        assertTrue(model.getConcessions().isEmpty());
        assertEquals(0, stats.getEntityStatistics(Ticket.class.getName()).getLoadCount(),
                "Sold seats must use scalar IDs, never hydrate tickets");
        assertEquals(0, stats.getEntityStatistics(Combo.class.getName()).getLoadCount());
        assertEquals(1, stats.getEntityStatistics(Seat.class.getName()).getLoadCount(),
                "Only the held seat is hydrated, the room layout uses scalar rows");
        assertEquals(3, stats.getPrepareStatementCount(), "Reading the model must not trigger lazy SQL");
    }

    @Test
    void unionPreservesSoldSeatsEvenWhenAlsoHeldByCurrentOrAnotherSession() {
        Fixture f = fixture();
        ticket(f.booking, f.showtime, f.seats.getFirst());
        ticket(f.booking, f.showtime, f.seats.get(1));
        Statistics stats = reset();
        var model = service.getSeatSelectionView(f.hold.getBookingHoldId()).orElseThrow();
        assertEquals(Set.of("A1", "A2", "A3", "A6", "A7"), new HashSet<>(model.getOccupiedSeats()));
        assertEquals(5, model.getOccupiedSeats().size(), "UNION must not duplicate sold-and-held seats");
        assertEquals(List.of("A1"), model.getSelectedSeats());
        var own = model.getSeatChoices().stream().filter(s -> s.getSeatCode().equals("A1")).findFirst().orElseThrow();
        assertTrue(own.isSelected()); assertTrue(own.isOccupied(), "A sold seat always remains unavailable");
        assertEquals(new BigDecimal("60000.00"), own.getPrice());
        assertEquals(3, stats.getPrepareStatementCount());
    }

    @Test
    void preservesEveryPriceCategoryAndEmptyRoomWithoutAdditionalQueries() {
        Fixture f = fixture();
        SeatTypePricing vip = new SeatTypePricing(); vip.setSeatType("VIP");
        vip.setMultiplier(new BigDecimal("1.50")); em.persist(vip);
        SeatTypePricing couple = new SeatTypePricing(); couple.setSeatType("Couple");
        couple.setMultiplier(new BigDecimal("2.00")); em.persist(couple);
        Seat vipSeat = seat(f.room, "B1"); vipSeat.setSeatType("VIP");
        Seat coupleSeat = seat(f.room, "C1"); coupleSeat.setSeatType("Couple");
        Statistics stats = reset();
        var model = service.getSeatSelectionView(f.hold.getBookingHoldId()).orElseThrow();
        assertEquals(0, new BigDecimal("97500").compareTo(model.seatPrice("B1")));
        assertEquals(0, new BigDecimal("130000").compareTo(model.seatPrice("C1")));
        assertEquals(3, stats.getPrepareStatementCount());
        Room empty = new Room(); empty.setRoomName("Empty Hall"); em.persist(empty);
        BookingHold emptyHold = hold(f, "Pending", 600);
        emptyHold.setShowtime(showtime(f.movie, empty));
        stats = reset();
        var emptyModel = service.getSeatSelectionView(emptyHold.getBookingHoldId()).orElseThrow();
        assertTrue(emptyModel.getSeatChoices().isEmpty()); assertTrue(emptyModel.getSelectedSeats().isEmpty());
        assertTrue(emptyModel.getOccupiedSeats().isEmpty());
        assertEquals(0, new BigDecimal("65000").compareTo(emptyModel.getStandardTicketPrice()));
        assertEquals(0, new BigDecimal("97500").compareTo(emptyModel.getVipTicketPrice()));
        assertEquals(0, new BigDecimal("130000").compareTo(emptyModel.getSweetboxTicketPrice()));
        assertEquals(3, stats.getPrepareStatementCount(), "Pricing is returned even when the room has no seats");
    }

    @Test
    void actualSeatTemplateRendersWithThreeContentQueries() throws Exception {
        Fixture f = fixture();
        Statistics stats = reset();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String html = mvc.perform(get("/booking/select-seats")
                        .with(user("seat@example.com").roles("KhachHang"))
                        .sessionAttr("BookingHoldId", f.hold.getBookingHoldId().toString()))
                .andExpect(status().isOk()).andExpect(view().name("booking/select-seats"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("Seat query movie"));
        assertTrue(html.contains("/booking/hold-seat"));
        assertTrue(html.contains("cosmos-site-footer"));
        assertEquals(3, stats.getPrepareStatementCount());
    }

    @Test
    void missingOrExpiredHoldDoesNotBuildSeatMap() {
        assertTrue(service.getSeatSelectionView(null).isEmpty());
        Fixture f = fixture();
        f.hold.setExpiresAtUtc(Instant.now().minusSeconds(1));
        Statistics stats = reset();
        assertTrue(service.getSeatSelectionView(f.hold.getBookingHoldId()).isEmpty());
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void holdsWithNoSeatsAndPaymentPendingHoldsRetainTheirBehavior() {
        Fixture f = fixture();
        BookingHold empty = hold(f, "Pending", 600);
        reset();
        assertTrue(service.getSeatSelectionView(empty.getBookingHoldId()).orElseThrow().getSelectedSeats().isEmpty());
        f.hold = em.find(BookingHold.class, f.hold.getBookingHoldId());
        f.hold.setStatus("PaymentPending");
        reset();
        assertTrue(service.getSeatSelectionView(f.hold.getBookingHoldId()).isEmpty());
    }

    @Test
    void selectingOccupiedAndSoldSeatsCannotCreateAnotherHold() {
        Fixture f = fixture();
        reset();
        assertEquals(BookingService.Status.CONFLICT,
                service.updateSeat(f.hold.getBookingHoldId(), f.seats.get(1).getSeatId(), true).status());
        assertEquals(BookingService.Status.CONFLICT,
                service.updateSeat(f.hold.getBookingHoldId(), f.seats.get(2).getSeatId(), true).status());
        assertEquals(BookingService.Status.SUCCESS,
                service.updateSeat(f.hold.getBookingHoldId(), f.seats.getFirst().getSeatId(), true).status());
        assertEquals(1L, em.createQuery("select count(h) from BookingSeatHold h where h.bookingHoldId=:id", Long.class)
                .setParameter("id", f.hold.getBookingHoldId()).getSingleResult());
    }

    @Test
    void concessionsAndCheckoutStillLoadCombosAndKeepHeldPrices() {
        Fixture f = fixture();
        reset();
        var model = service.getBookingView(f.hold.getBookingHoldId(), true).orElseThrow();
        assertEquals(1, model.getConcessions().size());
        var combo = model.getConcessions().getFirst();
        assertEquals("Popcorn", combo.getName());
        assertEquals(2, combo.getSelectedQuantity());
        assertEquals(new BigDecimal("30000.00"), combo.getPrice());
        assertEquals(9, combo.getStockQuantity());
        assertEquals(new BigDecimal("120000.00"), model.getGrandTotal());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 40})
    void concessionsAndCheckoutUseThreeQueriesWithoutLoadingSeatMap(int extras) {
        Fixture f = fixture();
        for (int i = 0; i < extras; i++) {
            seat(f.room, "X" + i);
            Combo combo = new Combo(); combo.setComboName("Extra " + i);
            combo.setComboPrice(new BigDecimal("50000")); combo.setQuantity(10); em.persist(combo);
        }
        for (boolean checkout : List.of(false, true)) {
            Statistics stats = reset();
            var model = (checkout ? service.getCheckoutView(f.hold.getBookingHoldId())
                    : service.getBookingView(f.hold.getBookingHoldId(), true)).orElseThrow();
            assertEquals(3, stats.getPrepareStatementCount());
            assertEquals(List.of("A1"), model.getSelectedSeats());
            assertEquals(1, model.getSeatChoices().size(), "Only the customer's selected seats are needed");
            assertTrue(model.getOccupiedSeats().isEmpty());
            assertEquals(checkout ? 1 : 1 + extras, model.getConcessions().size());
            var popcorn = model.getConcessions().stream().filter(c -> c.getName().equals("Popcorn")).findFirst().orElseThrow();
            assertEquals(2, popcorn.getSelectedQuantity()); assertEquals(9, popcorn.getStockQuantity());
            assertEquals(new BigDecimal("30000.00"), popcorn.getPrice());
            assertEquals(new BigDecimal("60000.00"), model.getTicketSubtotal());
            assertEquals(new BigDecimal("60000.00"), model.getConcessionSubtotal());
            assertEquals(new BigDecimal("120000.00"), model.getGrandTotal());
            assertEquals("Seat query movie", model.getMovieTitle());
            assertEquals("18:00", model.getSelectedTime()); assertEquals("2D", model.getCinemaFormat());
            assertNotNull(model.getBookingExpiresAtUtc());
            for (Class<?> type : List.of(Seat.class, Ticket.class, Combo.class, SeatTypePricing.class,
                    BookingSeatHold.class, BookingComboHold.class)) {
                assertEquals(0, stats.getEntityStatistics(type.getName()).getLoadCount(), type.getSimpleName());
            }
            assertEquals(3, stats.getPrepareStatementCount(), "Reading totals must not trigger lazy SQL");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/booking/select-concessions", "/booking/checkout"})
    void orderTemplatesRenderCompleteContentWithThreeQueries(String path) throws Exception {
        Fixture f = fixture();
        Statistics stats = reset();
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String html = mvc.perform(get(path).with(user("seat@example.com").roles("KhachHang"))
                        .sessionAttr("BookingHoldId", f.hold.getBookingHoldId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String expected : List.of("Seat query movie", "Popcorn", "120.000", "cosmos-site-footer", "</html>", "name=\"_csrf\"")) {
            assertTrue(html.contains(expected), expected);
        }
        assertTrue(html.contains(path.endsWith("checkout") ? "/booking/start-payment" : "/booking/update-combo-hold"));
        assertEquals(3, stats.getPrepareStatementCount());
    }

    @Test
    void checkoutWithoutCombosKeepsTicketTotalAndConcessionsStillShowOptions() {
        Fixture f = fixture();
        em.createQuery("delete from BookingComboHold h where h.bookingHoldId=:id")
                .setParameter("id", f.hold.getBookingHoldId()).executeUpdate();
        Statistics stats = reset();
        var checkout = service.getCheckoutView(f.hold.getBookingHoldId()).orElseThrow();
        assertTrue(checkout.getConcessions().isEmpty());
        assertEquals(new BigDecimal("60000.00"), checkout.getGrandTotal());
        assertEquals(3, stats.getPrepareStatementCount());
        reset();
        var options = service.getBookingView(f.hold.getBookingHoldId(), true).orElseThrow();
        assertEquals(1, options.getConcessions().size());
        assertEquals(0, options.getConcessions().getFirst().getSelectedQuantity());
        assertEquals(new BigDecimal("35000.00"), options.getConcessions().getFirst().getPrice());
        assertEquals(7, options.getConcessions().getFirst().getStockQuantity());
    }

    @Test
    void changingComboQuantityIsReflectedInBothOrderReadersAtHeldPrice() {
        Fixture f = fixture();
        int comboId = em.createQuery("select c.comboId from Combo c where c.comboName='Popcorn'", Integer.class)
                .getSingleResult();
        reset();
        var added = service.updateCombo(f.hold.getBookingHoldId(), comboId, 1);
        assertEquals(BookingService.Status.SUCCESS, added.status()); assertEquals(3, added.quantity());
        reset();
        for (var model : List.of(service.getBookingView(f.hold.getBookingHoldId(), true).orElseThrow(),
                service.getCheckoutView(f.hold.getBookingHoldId()).orElseThrow())) {
            assertEquals(3, model.getConcessions().getFirst().getSelectedQuantity());
            assertEquals(new BigDecimal("30000.00"), model.getConcessions().getFirst().getPrice());
            assertEquals(new BigDecimal("150000.00"), model.getGrandTotal());
        }
        assertEquals(BookingService.Status.SUCCESS, service.updateCombo(f.hold.getBookingHoldId(), comboId, -1).status());
        reset();
        assertEquals(new BigDecimal("120000.00"), service.getCheckoutView(f.hold.getBookingHoldId()).orElseThrow().getGrandTotal());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Cancelled", "Expired", "PaymentPending", "Processing"})
    void invalidOrderStateIsRejectedWithoutLoadingOrderLines(String state) {
        Fixture f = fixture(); f.hold.setStatus(state);
        Statistics stats = reset();
        assertTrue(service.getCheckoutView(f.hold.getBookingHoldId()).isEmpty());
        assertEquals(1, stats.getPrepareStatementCount());
        stats = reset();
        assertTrue(service.getBookingView(f.hold.getBookingHoldId(), true).isEmpty());
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void expiredMissingAndEmptyOrdersKeepExistingRedirects() throws Exception {
        Fixture f = fixture();
        f.hold.setExpiresAtUtc(Instant.now().minusSeconds(10)); reset();
        assertTrue(service.getCheckoutView(f.hold.getBookingHoldId()).isEmpty());
        assertTrue(service.getBookingView(f.hold.getBookingHoldId(), true).isEmpty());
        assertTrue(service.getCheckoutView(null).isEmpty());
        assertTrue(service.getCheckoutView(UUID.randomUUID()).isEmpty());
        BookingHold empty = hold(f, "Pending", 600); reset();
        for (String path : List.of("/booking/select-concessions", "/booking/checkout")) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mvc.perform(get(path).sessionAttr("BookingHoldId", empty.getBookingHoldId().toString()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(path.endsWith("checkout") ? "/booking" : "/booking/select-seats"));
        }
    }

    @Test
    void orderReadersRejectMoreThanFiveSeats() {
        Fixture f = fixture();
        for (int i=1; i<=5; i++) {
            Seat seat = seat(f.room, "B"+i);
            BookingSeatHold selected = new BookingSeatHold(); selected.setBookingHoldId(f.hold.getBookingHoldId());
            selected.setShowtimeId(f.showtime.getShowtimeId()); selected.setSeatId(seat.getSeatId());
            selected.setUnitPrice(new BigDecimal("60000")); selected.setHeldAtUtc(Instant.now()); em.persist(selected);
        }
        Statistics stats = reset();
        assertTrue(service.getCheckoutView(f.hold.getBookingHoldId()).isEmpty());
        assertEquals(2, stats.getPrepareStatementCount());
        stats = reset();
        assertTrue(service.getBookingView(f.hold.getBookingHoldId(), true).isEmpty());
        assertEquals(2, stats.getPrepareStatementCount());
    }

    @Test
    void limitAndDeselectRemainEnforced() {
        Fixture f = fixture();
        List<Integer> available = new ArrayList<>();
        for (int i=1; i<=5; i++) available.add(seat(f.room, "B"+i).getSeatId());
        reset();
        for (int i=0; i<4; i++) assertEquals(BookingService.Status.SUCCESS,
                service.updateSeat(f.hold.getBookingHoldId(), available.get(i), true).status());
        assertEquals(BookingService.Status.INVALID,
                service.updateSeat(f.hold.getBookingHoldId(), available.get(4), true).status());
        assertEquals(BookingService.Status.SUCCESS,
                service.updateSeat(f.hold.getBookingHoldId(), available.getFirst(), false).status());
        em.flush();
        assertEquals(BookingService.Status.SUCCESS,
                service.updateSeat(f.hold.getBookingHoldId(), available.get(4), true).status());
    }

    private Fixture fixture() {
        Fixture f = new Fixture();
        User user = new User(); user.setFullName("Seat tester"); user.setEmail(UUID.randomUUID()+"@example.com");
        user.setRole("KhachHang"); em.persist(user);
        Movie movie = new Movie(); movie.setTitle("Seat query movie");
        movie.setPosterUrl("/poster.jpg"); movie.setTrailer("https://youtu.be/test"); em.persist(movie); f.movie = movie;
        Room room = new Room(); room.setRoomName("Seat Hall"); em.persist(room); f.room = room;
        SeatTypePricing price = new SeatTypePricing(); price.setSeatType("Regular");
        price.setMultiplier(BigDecimal.ONE); em.persist(price);
        f.showtime = showtime(movie, room);
        Booking booking = new Booking(); booking.setUser(user); booking.setBookingDate(LocalDateTime.now());
        em.persist(booking); f.booking = booking;
        for (int i=1; i<=7; i++) f.seats.add(seat(room, "A"+i));
        f.hold = hold(f, "Pending", 600);
        seatHold(f, f.hold, 0);
        seatHold(f, hold(f, "Pending", 600), 1);
        ticket(booking, f.showtime, f.seats.get(2));
        seatHold(f, hold(f, "Pending", -60), 3);
        seatHold(f, hold(f, "Cancelled", 600), 4);
        seatHold(f, hold(f, "PaymentPending", 600), 5);
        seatHold(f, hold(f, "Processing", 600), 6);
        Combo combo = new Combo(); combo.setComboName("Popcorn"); combo.setComboPrice(new BigDecimal("35000"));
        combo.setQuantity(10); combo.setReservedQuantity(3); em.persist(combo);
        BookingComboHold ownCombo = new BookingComboHold(); ownCombo.setBookingHoldId(f.hold.getBookingHoldId());
        ownCombo.setComboId(combo.getComboId()); ownCombo.setQuantity(2);
        ownCombo.setUnitPrice(new BigDecimal("30000")); em.persist(ownCombo);
        return f;
    }

    private BookingHold hold(Fixture f, String status, int seconds) {
        BookingHold hold = new BookingHold(); hold.setBookingHoldId(UUID.randomUUID());
        hold.setMovie(f.movie); hold.setShowtime(f.showtime); hold.setUser(f.booking.getUser());
        hold.setStatus(status); hold.setCreatedAtUtc(Instant.now());
        hold.setExpiresAtUtc(Instant.now().plusSeconds(seconds)); em.persist(hold); return hold;
    }

    private void seatHold(Fixture f, BookingHold hold, int index) {
        BookingSeatHold seat = new BookingSeatHold(); seat.setBookingHoldId(hold.getBookingHoldId());
        seat.setShowtimeId(f.showtime.getShowtimeId()); seat.setSeatId(f.seats.get(index).getSeatId());
        seat.setUnitPrice(new BigDecimal("60000.00")); seat.setHeldAtUtc(Instant.now()); em.persist(seat);
    }

    private Seat seat(Room room, String code) {
        Seat seat = new Seat(); seat.setRoom(room); seat.setSeatCode(code); seat.setSeatType("Regular");
        em.persist(seat); return seat;
    }

    private Showtime showtime(Movie movie, Room room) {
        Showtime showtime = new Showtime(); showtime.setMovie(movie); showtime.setRoom(room);
        showtime.setDate(LocalDate.now().plusDays(1)); showtime.setStartTime(showtime.getDate().atTime(18, 0));
        showtime.setEndTime(showtime.getStartTime().plusHours(2)); showtime.setBasePrice(new BigDecimal("65000"));
        em.persist(showtime); return showtime;
    }

    private void ticket(Booking booking, Showtime showtime, Seat seat) {
        Ticket ticket = new Ticket(); ticket.setBooking(booking); ticket.setShowtime(showtime); ticket.setSeat(seat);
        ticket.setTicketCode(UUID.randomUUID().toString()); em.persist(ticket);
    }

    private Statistics reset() {
        em.flush(); em.clear();
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics(); stats.clear(); return stats;
    }

    private static class Fixture {
        Movie movie; Room room; Showtime showtime; Booking booking; BookingHold hold;
        List<Seat> seats = new ArrayList<>();
    }
}
