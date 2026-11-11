package com.cinema.management.repository.theater;

import com.cinema.management.model.entity.theater.Showtime;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.List;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, Integer> {
    List<Showtime> findByMovieMovieIdAndDateOrderByStartTime(
            Integer movieId, LocalDate date);
    List<Showtime> findByDateBetweenOrderByDateAscStartTimeAsc(
            LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"movie", "room"})
    @Query("select s from Showtime s where s.showtimeId = :showtimeId")
    Optional<Showtime> findDetailsById(@Param("showtimeId") Integer showtimeId);
}

