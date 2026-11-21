package com.cinema.management.repository.booking;

import com.cinema.management.model.entity.booking.Combo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.cinema.management.repository.booking.projection.ConcessionData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComboRepository extends JpaRepository<Combo, Integer> {
    @Query("""
            select new com.cinema.management.repository.booking.projection.ConcessionData(
                c.comboId, c.comboName, c.comboPrice, c.quantity, c.reservedQuantity, h.quantity, h.unitPrice)
            from Combo c left join BookingComboHold h on h.comboId = c.comboId and h.bookingHoldId = :holdId
            where :includeUnselected = true or h.quantity > 0
            """)
    List<ConcessionData> findConcessionData(@Param("holdId") UUID holdId,
                                           @Param("includeUnselected") boolean includeUnselected);

    List<Combo> findByQuantityGreaterThanOrderByComboName(int quantity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Combo c where c.comboId = :comboId")
    Optional<Combo> findByIdForUpdate(@Param("comboId") Integer comboId);
}

