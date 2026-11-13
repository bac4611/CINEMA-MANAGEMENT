package com.cinema.management.repository.theater;

import com.cinema.management.model.entity.theater.Showtime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.List;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, Integer> {
    List<Showtime> findByMovieMovieIdAndDateOrderByStartTime(
            Integer movieId, LocalDate date);
    List<Showtime> findByMovieMovieIdOrderByDateAscStartTimeAsc(Integer movieId);
    List<Showtime> findByDateBetweenOrderByDateAscStartTimeAsc(
            LocalDate from, LocalDate to);
    List<Showtime> findByDateOrderByRoomRoomIdAscStartTimeAsc(LocalDate date);
    List<Showtime> findByRoomRoomIdAndDateOrderByStartTime(Integer roomId, LocalDate date);
    List<Showtime> findByMovieMovieId(Integer movieId);

    @EntityGraph(attributePaths = {"movie", "room"})
    @Query("select s from Showtime s where s.showtimeId = :showtimeId")
    Optional<Showtime> findDetailsById(@Param("showtimeId") Integer showtimeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Showtime s where s.showtimeId = :showtimeId")
    Optional<Showtime> findByIdForUpdate(@Param("showtimeId") Integer showtimeId);
}

