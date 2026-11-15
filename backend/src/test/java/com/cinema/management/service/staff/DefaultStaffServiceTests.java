package com.cinema.management.service.staff;

import com.cinema.management.model.dto.request.SaveScheduleItemRequest;
import com.cinema.management.model.dto.request.SaveScheduleRequest;
import com.cinema.management.model.dto.request.StaffMovieRequest;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.theater.Room;
import com.cinema.management.model.entity.theater.Showtime;
import com.cinema.management.repository.booking.*;
import com.cinema.management.repository.movie.*;
import com.cinema.management.repository.payment.PaymentRepository;
import com.cinema.management.repository.theater.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultStaffServiceTests {
    private final MovieRepository movies = mock(MovieRepository.class);
    private final GenreRepository genres = mock(GenreRepository.class);
    private final MovieGenreRepository movieGenres = mock(MovieGenreRepository.class);
    private final MovieCastRepository movieCasts = mock(MovieCastRepository.class);
    private final MovieDirectorRepository movieDirectors = mock(MovieDirectorRepository.class);
    private final PersonRepository people = mock(PersonRepository.class);
    private final LanguageRepository languages = mock(LanguageRepository.class);
    private final CountryRepository countries = mock(CountryRepository.class);
    private final ShowtimeRepository showtimes = mock(ShowtimeRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final SeatRepository seats = mock(SeatRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final ComboRepository combos = mock(ComboRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private DefaultStaffService service;

    @BeforeEach
    void setUp() {
        service = new DefaultStaffService(movies, genres, movieGenres, movieCasts, movieDirectors,
                people, languages, countries, showtimes, rooms, seats, tickets, combos, payments);
    }

    @Test
    void twoDimensionalMovieCannotBeScheduledInImaxRoom() {
        Movie movie = new Movie(); movie.setMovieId(1); movie.setDuration((short) 120);
        movie.setTrailer("trailer\n[COSMOS_FORMATS:2D]");
        Room room = new Room(); room.setRoomId(2); room.setRoomName("IMAX 1");
        when(rooms.findAll()).thenReturn(List.of(room));
        when(movies.findAllById(any())).thenReturn(List.of(movie));
        SaveScheduleItemRequest item = new SaveScheduleItemRequest();
        item.setMovieId(1); item.setRoomId(2); item.setStartTime("10:00");
        SaveScheduleRequest request = new SaveScheduleRequest();
        request.setDate(LocalDate.of(2026, 11, 12)); request.setItems(List.of(item));

        var result = service.saveSchedule(request);

        assertEquals(StaffService.Status.INVALID, result.status());
        verifyNoInteractions(showtimes);
    }

    @Test
    void movieWithSoldTicketsCannotBeDeleted() {
        Movie movie = new Movie(); movie.setMovieId(1);
        Showtime showtime = new Showtime(); showtime.setShowtimeId(8);
        when(movies.findById(1)).thenReturn(Optional.of(movie));
        when(showtimes.findByMovieMovieId(1)).thenReturn(List.of(showtime));
        when(tickets.countByShowtimeShowtimeId(8)).thenReturn(1L);

        var result = service.deleteMovie(1);

        assertEquals(StaffService.Status.CONFLICT, result.status());
        verify(movies, never()).delete(any());
    }

    @Test
    void creatingMovieRejectsCaseAndWhitespaceDuplicate() {
        Movie existing = new Movie(); existing.setMovieId(1); existing.setTitle("Toy Story");
        when(movies.findAll()).thenReturn(List.of(existing));
        StaffMovieRequest form = new StaffMovieRequest(); form.setTitle("  TOY   story  "); form.setDuration((short)100);
        assertEquals(StaffService.Status.CONFLICT, service.createMovie(form).status());
        verify(movies, never()).saveAndFlush(any());
    }

    @Test
    void editingMovieCannotTakeAnotherMoviesTitle() {
        Movie first = new Movie(); first.setMovieId(1); first.setTitle("First");
        Movie other = new Movie(); other.setMovieId(2); other.setTitle("Other");
        when(movies.findById(1)).thenReturn(Optional.of(first));
        when(movies.findAll()).thenReturn(List.of(first, other));
        StaffMovieRequest form = new StaffMovieRequest(); form.setMovieId(1); form.setTitle("other"); form.setDuration((short)100);
        assertEquals(StaffService.Status.CONFLICT, service.updateMovie(form).status());
        verify(movies, never()).save(any());
    }

    @Test
    void movieWithPaymentOrHoldReferencesCannotBeDeleted() {
        Movie movie = new Movie(); movie.setMovieId(1);
        when(movies.findById(1)).thenReturn(Optional.of(movie));
        when(movies.countBookingReferences(1)).thenReturn(1L);
        assertEquals(StaffService.Status.CONFLICT, service.deleteMovie(1).status());
        verify(movies, never()).delete(any());
    }

    @Test
    void overlappingNewShowtimesAreRejectedBeforeAnyWrite() {
        Movie movie = new Movie(); movie.setMovieId(1); movie.setDuration((short)100);
        Room room = new Room(); room.setRoomId(1); room.setRoomName("Standard");
        when(rooms.findAll()).thenReturn(List.of(room));
        when(movies.findAllById(any())).thenReturn(List.of(movie));
        SaveScheduleItemRequest first = new SaveScheduleItemRequest(); first.setMovieId(1); first.setRoomId(1); first.setStartTime("10:00");
        SaveScheduleItemRequest second = new SaveScheduleItemRequest(); second.setMovieId(1); second.setRoomId(1); second.setStartTime("10:00");
        SaveScheduleRequest form = new SaveScheduleRequest(); form.setDate(LocalDate.of(2026,11,15)); form.setItems(List.of(first,second));
        assertEquals(StaffService.Status.CONFLICT, service.saveSchedule(form).status());
        verify(showtimes, never()).save(any());
        verify(showtimes, never()).deleteAll(any());
    }

    private SaveScheduleRequest screeningAt(String time) {
        Movie movie = new Movie(); movie.setMovieId(1); movie.setDuration((short)120);
        Room room = new Room(); room.setRoomId(1); room.setRoomName("Standard");
        when(rooms.findAll()).thenReturn(List.of(room));
        when(movies.findAllById(any())).thenReturn(List.of(movie));
        SaveScheduleItemRequest item = new SaveScheduleItemRequest(); item.setMovieId(1); item.setRoomId(1); item.setStartTime(time);
        SaveScheduleRequest form = new SaveScheduleRequest(); form.setDate(LocalDate.of(2026,11,16)); form.setItems(List.of(item));
        return form;
    }

    @Test
    void midnightScreeningIsAllowed() {
        assertEquals(StaffService.Status.SUCCESS, service.saveSchedule(screeningAt("00:00")).status());
        verify(showtimes).save(argThat(item -> item.getStartTime().getHour() == 0));
    }

    @Test
    void lateScreeningEndsOnFollowingDay() {
        assertEquals(StaffService.Status.SUCCESS, service.saveSchedule(screeningAt("23:59")).status());
        verify(showtimes).save(argThat(item -> item.getEndTime().equals(LocalDate.of(2026,11,17).atTime(1,59))));
    }

    @Test
    void previousDaysScreeningBlocksOverlappingMidnightSlot() {
        SaveScheduleRequest form = screeningAt("00:30");
        Showtime previous = new Showtime(); previous.setShowtimeId(90);
        previous.setMovie(new Movie()); previous.setRoom(rooms.findAll().get(0));
        previous.setStartTime(form.getDate().minusDays(1).atTime(23,30));
        previous.setEndTime(form.getDate().atTime(1,30));
        when(showtimes.findOverlappingWindow(any(), any())).thenReturn(List.of(previous));
        assertEquals(StaffService.Status.CONFLICT, service.saveSchedule(form).status());
        verify(showtimes, never()).save(any());
    }

    @Test
    void lateScreeningCannotOverlapNextDaysSlot() {
        SaveScheduleRequest form = screeningAt("23:59");
        Showtime next = new Showtime(); next.setShowtimeId(91); next.setMovie(new Movie()); next.setRoom(rooms.findAll().get(0));
        next.setStartTime(form.getDate().plusDays(1).atTime(1,0)); next.setEndTime(form.getDate().plusDays(1).atTime(3,0));
        when(showtimes.findOverlappingWindow(any(), any())).thenReturn(List.of(next));
        assertEquals(StaffService.Status.CONFLICT, service.saveSchedule(form).status());
        verify(showtimes, never()).save(any());
    }
}
