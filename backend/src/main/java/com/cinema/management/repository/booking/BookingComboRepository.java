package com.cinema.management.repository.booking;
import com.cinema.management.model.entity.booking.BookingCombo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BookingComboRepository extends JpaRepository<BookingCombo, BookingCombo.Key> {
    List<BookingCombo> findByBookingId(Integer bookingId);
    void deleteByBookingId(Integer bookingId);
}

