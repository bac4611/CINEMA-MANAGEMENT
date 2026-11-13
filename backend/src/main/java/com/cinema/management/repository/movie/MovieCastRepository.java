package com.cinema.management.repository.movie;
import com.cinema.management.model.entity.movie.MovieCast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface MovieCastRepository extends JpaRepository<MovieCast, MovieCast.Key> {
    List<MovieCast> findByMovieId(Integer movieId);

    @Query("""
            select p.fullName
            from MovieCast mc
            join Person p on p.personId = mc.personId
            where mc.movieId = :movieId
            order by p.fullName
            """)
    List<String> findPersonNamesByMovieId(@Param("movieId") Integer movieId);

    void deleteByMovieId(Integer movieId);
}

