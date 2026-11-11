package com.cinema.management.model.entity.movie;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "MovieGenres")
@IdClass(MovieGenre.Key.class)
@Getter @Setter @NoArgsConstructor
public class MovieGenre {
    @Id @Column(name = "MovieID") private Integer movieId;
    @Id @Column(name = "GenreID") private Integer genreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MovieID", insertable = false, updatable = false)
    private Movie movie;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GenreID", insertable = false, updatable = false)
    private Genre genre;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Key implements Serializable {
        private Integer movieId;
        private Integer genreId;
    }
}

