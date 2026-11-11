package com.cinema.management.model.viewmodel.home;
import lombok.*; import java.util.ArrayList; import java.util.List;
@Getter @Setter @NoArgsConstructor
public class OfferQuickBookingMovieViewModel {
    private int movieId; private String title = "";
    private List<OfferQuickBookingShowtimeViewModel> showtimes = new ArrayList<>();
}

