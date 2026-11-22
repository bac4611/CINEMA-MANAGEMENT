package com.cinema.management.repository.theater.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ShowtimeSelectionData(Integer showtimeId, Integer movieId, LocalDate date,
                                     LocalDateTime startTime, String roomName) {
}
