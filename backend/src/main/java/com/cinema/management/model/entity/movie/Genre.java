package com.cinema.management.model.entity.movie;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Genres")
@Getter @Setter @NoArgsConstructor
public class Genre {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "GenreID")
    private Integer genreId;
    @Column(nullable = false)
    private String name = "";

    @OneToMany(mappedBy = "genre")
    private List<MovieGenre> movieGenres = new ArrayList<>();
}

