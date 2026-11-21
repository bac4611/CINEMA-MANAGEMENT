package com.cinema.management.repository.booking;

import com.cinema.management.model.entity.booking.BookingSeatHold;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.cinema.management.repository.theater.projection.IdCount;
import com.cinema.management.repository.booking.projection.HeldSeatData;

import java.util.*;
import java.util.UUID;

public interface BookingSeatHoldRepository
        extends JpaRepository<BookingSeatHold, BookingSeatHold.Key> {
    @Query("""
            select new com.cinema.management.repository.booking.projection.HeldSeatData(
                h.seatId, s.seatCode, s.seatType, h.unitPrice)
            from BookingSeatHold h join h.seat s
            where h.bookingHoldId = :holdId order by s.seatCode
            """)
    List<HeldSeatData> findHeldSeatData(@Param("holdId") UUID holdId);

    @Query("""
            select new com.cinema.management.repository.theater.projection.IdCount(h.showtimeId, count(h))
            from BookingSeatHold h where h.showtimeId in :showtimeIds
              and h.bookingHold.status in ('Pending', 'PaymentPending', 'Processing')
              and h.bookingHold.expiresAtUtc > CURRENT_TIMESTAMP
            group by h.showtimeId
            """)
    List<IdCount> countActiveSeatsByShowtimeIds(@Param("showtimeIds") Collection<Integer> showtimeIds);

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

