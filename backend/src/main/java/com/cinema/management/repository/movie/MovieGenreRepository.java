package com.cinema.management.repository.movie;
import com.cinema.management.model.entity.movie.MovieGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MovieGenreRepository extends JpaRepository<MovieGenre, MovieGenre.Key> {
    List<MovieGenre> findByMovieId(Integer movieId);
    void deleteByMovieId(Integer movieId);
}

