package com.cinema.management.repository.movie;

import com.cinema.management.model.entity.movie.Movie;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Integer> {
    List<Movie> findByTitleContainingIgnoreCaseOrderByReleaseDateDesc(String title);
    List<Movie> findByReleaseDateGreaterThanEqualOrderByReleaseDate(LocalDate releaseDate);

    /*
     * Do not join-fetch casts and directors together here. Both associations
     * are List-valued bags, and Hibernate rejects fetching multiple bags in a
     * single query with MultipleBagFetchException. The booking service is
     * transactional, so those two collections can be initialized separately
     * when the view model is assembled.
     */
    @EntityGraph(attributePaths = {"country", "language", "movieGenres.genre"})
    @Query("select m from Movie m where m.movieId = :movieId")
    Optional<Movie> findDetailsById(@Param("movieId") Integer movieId);

    @EntityGraph(attributePaths = {"movieGenres.genre"})
    @Query("select distinct m from Movie m")
    List<Movie> findAllForChatbot();
}

