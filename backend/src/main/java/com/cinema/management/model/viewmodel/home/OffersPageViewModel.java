package com.cinema.management.model.viewmodel.home;
import lombok.*; import java.util.ArrayList; import java.util.List;
@Getter @Setter @NoArgsConstructor
public class OffersPageViewModel {
    private List<OfferViewModel> offers = new ArrayList<>();
    private List<OfferViewModel> featuredOffers = new ArrayList<>();
    private List<OfferViewModel> expiringSoonOffers = new ArrayList<>();
    private List<OfferQuickBookingMovieViewModel> quickBookingMovies = new ArrayList<>();
}

