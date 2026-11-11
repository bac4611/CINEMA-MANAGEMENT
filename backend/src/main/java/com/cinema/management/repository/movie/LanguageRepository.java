package com.cinema.management.repository.movie;
import com.cinema.management.model.entity.movie.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface LanguageRepository extends JpaRepository<Language, Integer> {
    Optional<Language> findByLanguageNameIgnoreCase(String languageName);
}

