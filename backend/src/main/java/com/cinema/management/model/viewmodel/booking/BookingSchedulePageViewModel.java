package com.cinema.management.model.viewmodel.booking;

import lombok.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class BookingSchedulePageViewModel {
    private LocalDate today;
    private LocalDate selectedDate;
    private Integer movieId;
    private String movieFilterTitle;
    private List<BookingScheduleDateOption> dateOptions = new ArrayList<>();
    private List<BookingScheduleMovieViewModel> movies = new ArrayList<>();
}

