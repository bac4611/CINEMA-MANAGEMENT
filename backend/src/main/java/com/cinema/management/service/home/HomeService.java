package com.cinema.management.service.home;

import com.cinema.management.model.viewmodel.home.MovieReviewFormViewModel;
import com.cinema.management.model.viewmodel.home.MovieViewModel;
import com.cinema.management.model.viewmodel.home.TicketPricingViewModel;

import java.util.List;
import java.util.Optional;

public interface HomeService {
    List<MovieViewModel> getHomeMovies();
    MovieCatalog getMovieCatalog();
    Optional<MovieViewModel> getMovieDetails(int movieId);
    boolean isActiveUser(Integer userId);
    ReviewResult addReview(Integer userId, MovieReviewFormViewModel form);
    TicketPricingViewModel getTicketPricing();

    enum ReviewStatus { SUCCESS, INVALID, NOT_FOUND, UNAUTHORIZED, PERSISTENCE_ERROR }
    record ReviewResult(ReviewStatus status, int movieId, String message) { }
    record MovieCatalog(List<MovieViewModel> featured,
                        List<MovieViewModel> nowShowing,
                        List<MovieViewModel> comingSoon) { }
}
