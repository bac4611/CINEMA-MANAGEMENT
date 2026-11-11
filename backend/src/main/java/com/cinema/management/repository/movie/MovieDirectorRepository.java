package com.cinema.management.repository.movie;
import com.cinema.management.model.entity.movie.MovieDirector;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MovieDirectorRepository extends JpaRepository<MovieDirector, MovieDirector.Key> {
    List<MovieDirector> findByMovieId(Integer movieId);
    void deleteByMovieId(Integer movieId);
}

