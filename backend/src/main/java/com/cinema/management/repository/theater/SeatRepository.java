package com.cinema.management.repository.theater;

import com.cinema.management.model.entity.theater.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface SeatRepository extends JpaRepository<Seat, Integer> {
    List<Seat> findByRoomRoomIdOrderBySeatCode(Integer roomId);
    Optional<Seat> findByRoomRoomIdAndSeatCodeIgnoreCase(Integer roomId, String seatCode);
    List<Seat> findBySeatIdIn(Collection<Integer> seatIds);
}

