package com.cinema.management.repository.movie;
import com.cinema.management.model.entity.movie.MovieDirector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface MovieDirectorRepository extends JpaRepository<MovieDirector, MovieDirector.Key> {
    List<MovieDirector> findByMovieId(Integer movieId);

    @Query("""
            select p.fullName
            from MovieDirector md
            join Person p on p.personId = md.personId
            where md.movieId = :movieId
            order by p.fullName
            """)
    List<String> findPersonNamesByMovieId(@Param("movieId") Integer movieId);

    void deleteByMovieId(Integer movieId);
}

