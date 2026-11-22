package com.cinema.management.repository.booking;
import com.cinema.management.model.entity.booking.BookingComboHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cinema.management.repository.booking.projection.HeldComboData;
import java.util.List;
import java.util.UUID;
public interface BookingComboHoldRepository
        extends JpaRepository<BookingComboHold, BookingComboHold.Key> {
    @Query("""
            select new com.cinema.management.repository.booking.projection.HeldComboData(
                h.comboId, c.comboName, h.unitPrice, h.quantity)
            from BookingComboHold h left join h.combo c where h.bookingHoldId = :holdId
            """)
    List<HeldComboData> findHeldComboData(@Param("holdId") UUID holdId);

    List<BookingComboHold> findByBookingHoldId(UUID bookingHoldId);
    void deleteByBookingHoldId(UUID bookingHoldId);
}

