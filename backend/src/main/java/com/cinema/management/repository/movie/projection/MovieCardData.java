package com.cinema.management.repository.movie.projection;

import java.time.LocalDate;

/** Scalar data for public movie cards; no lazy entity relationships are loaded. */
public record MovieCardData(Integer movieId, String title, short duration, String posterUrl,
                            String synopsis, LocalDate releaseDate, String ageRating, String trailer) {
}
