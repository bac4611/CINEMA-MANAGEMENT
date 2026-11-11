package com.cinema.management.repository.movie;
import com.cinema.management.model.entity.movie.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface GenreRepository extends JpaRepository<Genre, Integer> {
    Optional<Genre> findByNameIgnoreCase(String name);
}

