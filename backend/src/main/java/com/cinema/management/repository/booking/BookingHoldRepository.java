package com.cinema.management.repository.booking;

import com.cinema.management.model.entity.booking.BookingHold;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface BookingHoldRepository extends JpaRepository<BookingHold, UUID> {
    List<BookingHold> findByStatusInAndExpiresAtUtcBefore(
            Collection<String> statuses, Instant expiresAtUtc);
    List<BookingHold> findByUserUserIdAndStatusOrderByCreatedAtUtcDesc(
            Integer userId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from BookingHold h where h.bookingHoldId = :holdId")
    Optional<BookingHold> findByIdForUpdate(@Param("holdId") UUID holdId);

    @EntityGraph(attributePaths = {"movie", "showtime", "seatHolds.seat", "comboHolds.combo"})
    @Query("select h from BookingHold h where h.bookingHoldId = :holdId")
    Optional<BookingHold> findDetailsById(@Param("holdId") UUID holdId);
}

