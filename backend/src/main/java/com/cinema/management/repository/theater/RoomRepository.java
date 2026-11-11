package com.cinema.management.repository.theater;
import com.cinema.management.model.entity.theater.Room;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface RoomRepository extends JpaRepository<Room, Integer> {
    @EntityGraph(attributePaths = "seats")
    Optional<Room> findWithSeatsByRoomId(Integer roomId);
}

