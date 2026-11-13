package com.cinema.management.repository.booking;

import com.cinema.management.model.entity.booking.BookingSeatHold;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;
import java.util.UUID;

public interface BookingSeatHoldRepository
        extends JpaRepository<BookingSeatHold, BookingSeatHold.Key> {
    boolean existsByShowtimeIdAndSeatId(Integer showtimeId, Integer seatId);
    Optional<BookingSeatHold> findByShowtimeIdAndSeatId(Integer showtimeId, Integer seatId);
    List<BookingSeatHold> findByBookingHoldId(UUID bookingHoldId);
    void deleteByBookingHoldId(UUID bookingHoldId);

    @Query("""
            select h.seatId from BookingSeatHold h
            where h.showtimeId = :showtimeId
              and h.bookingHold.status in ('Pending', 'PaymentPending', 'Processing')
              and h.bookingHold.expiresAtUtc > CURRENT_TIMESTAMP
            """)
    Set<Integer> findActiveHeldSeatIds(@Param("showtimeId") Integer showtimeId);
}

