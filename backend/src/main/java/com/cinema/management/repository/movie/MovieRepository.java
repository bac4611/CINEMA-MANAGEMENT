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

    @EntityGraph(attributePaths = {
            "country", "language", "movieGenres.genre", "movieCasts.person", "movieDirectors.person"
    })
    @Query("select m from Movie m where m.movieId = :movieId")
    Optional<Movie> findDetailsById(@Param("movieId") Integer movieId);

    @EntityGraph(attributePaths = {"movieGenres.genre"})
    @Query("select distinct m from Movie m")
    List<Movie> findAllForChatbot();
}

