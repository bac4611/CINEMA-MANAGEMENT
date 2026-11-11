package com.cinema.management.repository.booking;
import com.cinema.management.model.entity.booking.BookingComboHold;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface BookingComboHoldRepository
        extends JpaRepository<BookingComboHold, BookingComboHold.Key> {
    List<BookingComboHold> findByBookingHoldId(UUID bookingHoldId);
    void deleteByBookingHoldId(UUID bookingHoldId);
}

