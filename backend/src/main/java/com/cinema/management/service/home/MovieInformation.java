package com.cinema.management.service.home;

import java.time.LocalDate;

/** Immutable, public movie metadata only; never contains entities or user/session data. */
public record MovieInformation(int movieId, String title, short duration, String posterUrl,
                               String synopsis, LocalDate releaseDate, String ageRating, String trailer,
                               Integer languageId, String languageName, Integer countryId, String countryName,
                               String genre, String director, String cast) {
}
