package com.cinema.management.repository.theater;

import com.cinema.management.model.entity.theater.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cinema.management.repository.theater.projection.IdCount;

import java.util.*;

public interface SeatRepository extends JpaRepository<Seat, Integer> {
    @Query("""
            select new com.cinema.management.repository.theater.projection.IdCount(s.room.roomId, count(s))
            from Seat s where s.room.roomId in :roomIds group by s.room.roomId
            """)
    List<IdCount> countSeatsByRoomIds(@Param("roomIds") Collection<Integer> roomIds);

    List<Seat> findByRoomRoomIdOrderBySeatCode(Integer roomId);
    Optional<Seat> findByRoomRoomIdAndSeatCodeIgnoreCase(Integer roomId, String seatCode);
    List<Seat> findBySeatIdIn(Collection<Integer> seatIds);
}

