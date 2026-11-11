package com.cinema.management.repository.booking;

import com.cinema.management.model.entity.booking.Combo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComboRepository extends JpaRepository<Combo, Integer> {
    List<Combo> findByQuantityGreaterThanOrderByComboName(int quantity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Combo c where c.comboId = :comboId")
    Optional<Combo> findByIdForUpdate(@Param("comboId") Integer comboId);
}

