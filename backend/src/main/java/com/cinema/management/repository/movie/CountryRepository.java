package com.cinema.management.repository.movie;
import com.cinema.management.model.entity.movie.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CountryRepository extends JpaRepository<Country, Integer> {
    Optional<Country> findByCountryNameIgnoreCase(String countryName);
}

