package com.cinema.management.repository.booking;

import com.cinema.management.model.entity.booking.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.*;

public interface TicketRepository extends JpaRepository<Ticket, Integer> {
    Optional<Ticket> findByTicketCode(String ticketCode);
    boolean existsByShowtimeShowtimeIdAndSeatSeatId(Integer showtimeId, Integer seatId);
    List<Ticket> findByBookingBookingId(Integer bookingId);
    long countByShowtimeShowtimeId(Integer showtimeId);

    @EntityGraph(attributePaths = {"booking.user", "showtime.movie", "showtime.room", "seat"})
    List<Ticket> findByShowtimeShowtimeId(Integer showtimeId);

    @EntityGraph(attributePaths = {"booking.user", "showtime.movie", "showtime.room", "seat"})
    List<Ticket> findAllByOrderByShowtimeStartTimeDesc();
}

