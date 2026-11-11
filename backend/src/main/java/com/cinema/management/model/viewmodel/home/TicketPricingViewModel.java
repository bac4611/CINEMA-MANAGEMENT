package com.cinema.management.model.viewmodel.home;
import lombok.*; import java.util.ArrayList; import java.util.List;
@Getter @Setter @NoArgsConstructor
public class TicketPricingViewModel {
    private List<MoviePricingViewModel> movieFormats = new ArrayList<>();
    private List<String> footnotes = new ArrayList<>();
}

