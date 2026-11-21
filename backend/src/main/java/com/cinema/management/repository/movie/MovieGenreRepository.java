package com.cinema.management.repository.movie;
import com.cinema.management.model.entity.movie.MovieGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cinema.management.repository.movie.projection.MovieGenreName;
import java.util.Collection;
import java.util.List;
public interface MovieGenreRepository extends JpaRepository<MovieGenre, MovieGenre.Key> {
    @Query("""
            select new com.cinema.management.repository.movie.projection.MovieGenreName(mg.movieId, g.name)
            from MovieGenre mg join mg.genre g
            where mg.movieId in :movieIds
            order by mg.movieId, mg.genreId
            """)
    List<MovieGenreName> findNamesByMovieIds(@Param("movieIds") Collection<Integer> movieIds);

    List<MovieGenre> findByMovieId(Integer movieId);
    void deleteByMovieId(Integer movieId);
}

