package com.cinema.management.repository.movie;
import com.cinema.management.model.entity.movie.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PersonRepository extends JpaRepository<Person, Integer> {
    List<Person> findByFullNameContainingIgnoreCaseOrderByFullName(String fullName);
}

