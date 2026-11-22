package com.cinema.management.service.home;

import com.cinema.management.model.entity.movie.Genre;
import com.cinema.management.model.entity.movie.MovieGenre;
import com.cinema.management.repository.movie.MovieCastRepository;
import com.cinema.management.repository.movie.MovieDirectorRepository;
import com.cinema.management.repository.movie.MovieRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MovieInformationCache {
    private final MovieRepository movies;
    private final MovieDirectorRepository directors;
    private final MovieCastRepository casts;
    private final Cache<Integer, MovieInformation> cache;

    @Autowired
    public MovieInformationCache(MovieRepository movies, MovieDirectorRepository directors,
                                 MovieCastRepository casts,
                                 @Value("${cinema.cache.movie-information.spec:maximumSize=1000,expireAfterWrite=15m}") String spec) {
        this(movies, directors, casts, Caffeine.from(spec).build());
    }

    MovieInformationCache(MovieRepository movies, MovieDirectorRepository directors,
                          MovieCastRepository casts, Cache<Integer, MovieInformation> cache) {
        this.movies = movies;
        this.directors = directors;
        this.casts = casts;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public Optional<MovieInformation> get(int movieId) {
        // A caller's write transaction may contain uncommitted metadata. Never
        // read shared cache or publish those changes to other requests.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return Optional.ofNullable(load(movieId));
        }
        // Atomic per-key loading also prevents concurrent cache misses from
        // issuing the same three metadata queries repeatedly. Null is not cached.
        return Optional.ofNullable(cache.get(movieId, this::load));
    }

    public void evictAfterCommit(int movieId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.invalidate(movieId);
                }
            });
        } else {
            cache.invalidate(movieId);
        }
    }

    private MovieInformation load(int movieId) {
        return movies.findDetailsById(movieId).map(movie -> new MovieInformation(
                movie.getMovieId(), movie.getTitle(), movie.getDuration(), movie.getPosterUrl(),
                movie.getSynopsis(), movie.getReleaseDate(), movie.getAgeRating(), movie.getTrailer(),
                movie.getLanguage() == null ? null : movie.getLanguage().getLanguageId(),
                movie.getLanguage() == null ? "" : movie.getLanguage().getLanguageName(),
                movie.getCountry() == null ? null : movie.getCountry().getCountryId(),
                movie.getCountry() == null ? "" : movie.getCountry().getCountryName(),
                movie.getMovieGenres().stream().map(MovieGenre::getGenre).filter(Objects::nonNull)
                        .map(Genre::getName).collect(Collectors.joining(", ")),
                String.join(", ", directors.findPersonNamesByMovieId(movieId)),
                String.join(", ", casts.findPersonNamesByMovieId(movieId)))).orElse(null);
    }
}
