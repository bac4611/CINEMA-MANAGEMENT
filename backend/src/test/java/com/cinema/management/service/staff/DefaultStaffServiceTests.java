package com.cinema.management.service.staff;

import com.cinema.management.model.dto.request.SaveScheduleItemRequest;
import com.cinema.management.model.dto.request.SaveScheduleRequest;
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
}
