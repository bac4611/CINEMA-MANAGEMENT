package com.cinema.management.model.entity.movie;

import com.cinema.management.model.entity.theater.Showtime;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Movies")
@Getter @Setter @NoArgsConstructor
public class Movie {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MovieID")
    private Integer movieId;

    @NotBlank @Size(max = 200)
    private String title = "";
    private LocalDate releaseDate;
    @Size(max = 10) private String ageRating = "--";
    private short duration;
    @Size(max = 1000) private String synopsis = "Chưa có tóm tắt";
    @NotBlank @Size(max = 1000) private String posterUrl = "";
    @NotBlank @Size(max = 1000) private String trailer = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CountryID")
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LanguageID")
    private Language language;

    @OneToMany(mappedBy = "movie")
    private List<MovieGenre> movieGenres = new ArrayList<>();
    @OneToMany(mappedBy = "movie")
    private List<MovieCast> movieCasts = new ArrayList<>();
    @OneToMany(mappedBy = "movie")
    private List<MovieDirector> movieDirectors = new ArrayList<>();
    @OneToMany(mappedBy = "movie")
    private List<Showtime> showtimes = new ArrayList<>();
}

