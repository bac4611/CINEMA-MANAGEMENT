package com.cinema.management.repository.theater.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookingScheduleData(Integer movieId, String title, String posterUrl, short duration,
                                  String ageRating, String languageName, Integer showtimeId,
                                  LocalDate date, LocalDateTime startTime, Integer roomId, String roomName) {
}
