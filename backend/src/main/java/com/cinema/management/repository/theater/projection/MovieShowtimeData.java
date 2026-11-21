package com.cinema.management.repository.theater.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Only the fields used by the seven-day schedule on the movie details page. */
public record MovieShowtimeData(Integer showtimeId, LocalDate date, LocalDateTime startTime, String roomName) {
}
