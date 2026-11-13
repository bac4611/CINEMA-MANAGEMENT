package com.cinema.management.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class StaffMovieRequest {
    private int movieId;
    @NotBlank private String title = "";
    private String synopsis = "";
    private String trailer = "";
    private String trailerUrl = "";
    private String posterUrl = "";
    private LocalDate releaseDate;
    @Min(1) private short duration;
    private String ageRating = "";
    private String genre = "";
    private String director = "";
    private String cast = "";
    private String language = "";
    private String country = "";
    private List<String> availableFormats = new ArrayList<>();
}
