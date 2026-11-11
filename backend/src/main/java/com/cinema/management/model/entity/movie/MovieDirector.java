package com.cinema.management.model.entity.movie;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "MovieDirectors")
@IdClass(MovieDirector.Key.class)
@Getter @Setter @NoArgsConstructor
public class MovieDirector {
    @Id @Column(name = "MovieID") private Integer movieId;
    @Id @Column(name = "PersonID") private Integer personId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MovieID", insertable = false, updatable = false)
    private Movie movie;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PersonID", insertable = false, updatable = false)
    private Person person;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Key implements Serializable {
        private Integer movieId;
        private Integer personId;
    }
}

