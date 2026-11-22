package com.cinema.management.service.home;

import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.repository.movie.MovieCastRepository;
import com.cinema.management.repository.movie.MovieDirectorRepository;
import com.cinema.management.repository.movie.MovieRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MovieInformationCacheTests {
    private final MovieRepository movies = mock(MovieRepository.class);
    private final MovieDirectorRepository directors = mock(MovieDirectorRepository.class);
    private final MovieCastRepository casts = mock(MovieCastRepository.class);
    private final AtomicLong time = new AtomicLong();
    private final MovieInformationCache cache = new MovieInformationCache(movies, directors, casts,
            Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(15))
                    .ticker(time::get).build());

    @Test
    void metadataExpiresFifteenMinutesAfterWriteEvenWithFrequentReads() {
        Movie movie = movie(1);
        when(movies.findDetailsById(1)).thenReturn(Optional.of(movie));
        assertEquals("Original", cache.get(1).orElseThrow().title());
        movie.setTitle("Changed outside the application");
        time.set(Duration.ofMinutes(14).toNanos());
        assertEquals("Original", cache.get(1).orElseThrow().title());
        time.set(Duration.ofMinutes(16).toNanos());
        assertEquals("Changed outside the application", cache.get(1).orElseThrow().title());
        verify(movies, times(2)).findDetailsById(1);
    }

    @Test
    void notFoundAndFailedLoadsAreNeverCached() {
        when(movies.findDetailsById(1)).thenReturn(Optional.empty()).thenReturn(Optional.of(movie(1)));
        assertTrue(cache.get(1).isEmpty());
        assertTrue(cache.get(1).isPresent());
        when(movies.findDetailsById(2)).thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn(Optional.of(movie(2)));
        assertThrows(IllegalStateException.class, () -> cache.get(2));
        assertTrue(cache.get(2).isPresent());
    }

    @Test
    void invalidationWaitsForCommitAndOnlyRemovesTheEditedMovie() {
        when(movies.findDetailsById(1)).thenReturn(Optional.of(movie(1)));
        when(movies.findDetailsById(2)).thenReturn(Optional.of(movie(2)));
        cache.get(1); cache.get(2);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try {
            cache.evictAfterCommit(1);
            cache.get(1);
            verify(movies).findDetailsById(1);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.clear();
        }
        cache.get(1); cache.get(2);
        verify(movies, times(2)).findDetailsById(1);
        verify(movies).findDetailsById(2);
    }

    @Test
    void simultaneousMissesOnlyLoadMetadataOnce() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(movies.findDetailsById(1)).thenAnswer(invocation -> {
            loading.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return Optional.of(movie(1));
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> cache.get(1).orElseThrow());
            assertTrue(loading.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> cache.get(1).orElseThrow());
            release.countDown();
            assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
        verify(movies).findDetailsById(1);
        verify(directors).findPersonNamesByMovieId(1);
        verify(casts).findPersonNamesByMovieId(1);
    }

    private Movie movie(int id) {
        Movie movie = new Movie(); movie.setMovieId(id); movie.setTitle("Original");
        return movie;
    }
}
