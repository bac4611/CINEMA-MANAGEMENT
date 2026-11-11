package com.cinema.management.repository.movie;
import com.cinema.management.model.entity.movie.MovieCast;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MovieCastRepository extends JpaRepository<MovieCast, MovieCast.Key> {
    List<MovieCast> findByMovieId(Integer movieId);
    void deleteByMovieId(Integer movieId);
}

