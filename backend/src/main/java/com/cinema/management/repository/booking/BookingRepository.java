package com.cinema.management.repository.booking;

import com.cinema.management.model.entity.booking.Booking;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Integer> {
    List<Booking> findByUserUserIdAndStatusOrderByBookingDateDesc(Integer userId, String status);

    List<Booking> findByUserUserIdOrderByBookingDateDesc(Integer userId);
    List<Booking> findByStatusOrderByBookingDateDesc(String status);

    @EntityGraph(attributePaths = {
            "user", "tickets.showtime.movie", "tickets.seat", "bookingCombos.combo", "payments"
    })
    @Query("select b from Booking b where b.bookingId = :bookingId")
    Optional<Booking> findDetailsById(@Param("bookingId") Integer bookingId);

    @Query("select b from Booking b where b.bookingId = :bookingId and b.user.userId = :userId")
    Optional<Booking> findDetailsByIdAndUserId(@Param("bookingId") Integer bookingId,
                                               @Param("userId") Integer userId);

    long countByStatus(String status);
}

