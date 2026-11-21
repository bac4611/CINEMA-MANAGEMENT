package com.cinema.management.repository.theater;

import com.cinema.management.model.entity.theater.Showtime;
import com.cinema.management.repository.theater.projection.MovieShowtimeData;
import com.cinema.management.repository.theater.projection.BookingScheduleData;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.List;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, Integer> {
    @Query("""
            select new com.cinema.management.repository.theater.projection.BookingScheduleData(
                m.movieId, m.title, m.posterUrl, m.duration, m.ageRating, l.languageName,
                s.showtimeId, s.date, s.startTime, r.roomId, r.roomName)
            from Movie m
            left join m.language l
            left join m.showtimes s on s.date between :fromDate and :toDate and s.startTime >= :now
            left join s.room r
            where (:movieId is null or m.movieId = :movieId)
              and (s.showtimeId is not null or :movieId is not null)
            order by s.date, s.startTime
            """)
    List<BookingScheduleData> findBookingSchedule(@Param("movieId") Integer movieId,
                                                 @Param("fromDate") LocalDate fromDate,
                                                 @Param("toDate") LocalDate toDate,
                                                 @Param("now") LocalDateTime now);

    @Query("""
            select new com.cinema.management.repository.theater.projection.MovieShowtimeData(
                s.showtimeId, s.date, s.startTime, r.roomName)
            from Showtime s left join s.room r
            where s.movie.movieId = :movieId
              and s.date >= :fromDate and s.date < :untilDate
            order by s.date, s.startTime
            """)
    List<MovieShowtimeData> findMovieSchedule(@Param("movieId") Integer movieId,
                                            @Param("fromDate") LocalDate fromDate,
                                            @Param("untilDate") LocalDate untilDate);

    @Query("select s from Showtime s where s.startTime < :until and s.endTime > :from")
    List<Showtime> findOverlappingWindow(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until);
    @Query(value = """
            SELECT (SELECT COUNT(*) FROM PaymentIntents WHERE ShowtimeID = :showtimeId)
                 + (SELECT COUNT(*) FROM BookingHolds WHERE ShowtimeID = :showtimeId)
                 + (SELECT COUNT(*) FROM BookingSeatHolds WHERE ShowtimeID = :showtimeId)
            """, nativeQuery = true)
    long countBookingReferences(@Param("showtimeId") Integer showtimeId);
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

