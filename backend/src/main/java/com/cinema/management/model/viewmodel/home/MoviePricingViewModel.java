package com.cinema.management.model.viewmodel.home;
import lombok.*; import java.util.ArrayList; import java.util.List;
@Getter @Setter @NoArgsConstructor
public class MoviePricingViewModel {
    private String id = ""; private String tabLabel = "";
    private List<SeatPricingViewModel> seatPrices = new ArrayList<>();
}

