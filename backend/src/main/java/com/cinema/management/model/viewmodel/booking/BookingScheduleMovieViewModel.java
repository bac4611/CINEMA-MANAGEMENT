package com.cinema.management.model.viewmodel.booking;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class BookingScheduleMovieViewModel {
    private int movieId;
    private String title = "";
    private String posterUrl = "";
    private String genres = "";
    private int durationMinutes;
    private String ageRating = "";
    private List<String> formats = new ArrayList<>();
    private List<BookingScheduleShowtimeViewModel> showtimes = new ArrayList<>();
}

