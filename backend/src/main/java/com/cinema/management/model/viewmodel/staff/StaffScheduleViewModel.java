package com.cinema.management.model.viewmodel.staff;

import com.cinema.management.model.viewmodel.home.MovieViewModel;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class StaffScheduleViewModel {
    private List<MovieViewModel> movies = new ArrayList<>();
    private List<StaffRoomSummaryViewModel> rooms = new ArrayList<>();
}

