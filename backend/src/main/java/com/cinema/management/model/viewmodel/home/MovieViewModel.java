package com.cinema.management.model.viewmodel.home;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.cinema.management.model.viewmodel.booking.ShowtimeChoiceViewModel;

@Getter @Setter @NoArgsConstructor
public class MovieViewModel {
    private int movieId;
    private String title = "";
    private short duration;
    private String posterUrl = "";
    private String synopsis = "";
    private LocalDate releaseDate;
    private String ageRating = "";
    private String trailer = "";
    private String genre = "";
    private String movieCast = "";
    private String movieDirector = "";
    private BigDecimal averageRating = BigDecimal.ZERO;
    private int reviewCount;
    private MovieReviewSummaryViewModel reviewSummary = new MovieReviewSummaryViewModel();
    private List<MovieReviewViewModel> reviews = new ArrayList<>();
    private List<ShowtimeChoiceViewModel> showtimes = new ArrayList<>();
    private String availableFormats = "2D";
    private Integer languageId;
    private String languageName = "";
    private Integer countryId;
    private String countryName = "";
}

