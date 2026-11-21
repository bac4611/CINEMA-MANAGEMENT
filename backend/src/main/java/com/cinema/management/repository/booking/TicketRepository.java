package com.cinema.management.repository.booking;

import com.cinema.management.model.entity.booking.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface TicketRepository extends JpaRepository<Ticket, Integer> {
    @Query("select t.seat.seatId from Ticket t where t.showtime.showtimeId = :showtimeId")
    Set<Integer> findSoldSeatIds(@Param("showtimeId") Integer showtimeId);

    Optional<Ticket> findByTicketCode(String ticketCode);
    boolean existsByShowtimeShowtimeIdAndSeatSeatId(Integer showtimeId, Integer seatId);
    List<Ticket> findByBookingBookingId(Integer bookingId);
    long countByShowtimeShowtimeId(Integer showtimeId);

    @EntityGraph(attributePaths = {"booking.user", "showtime.movie", "showtime.room", "seat"})
    List<Ticket> findByShowtimeShowtimeId(Integer showtimeId);

    @EntityGraph(attributePaths = {"booking.user", "showtime.movie", "showtime.room", "seat"})
    List<Ticket> findAllByOrderByShowtimeStartTimeDesc();
}

