package com.cinema.management.controller.home;

import com.cinema.management.model.viewmodel.home.MovieReviewFormViewModel;
import com.cinema.management.service.home.HomeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping({"/Home", "/home"})
public class HomeController {
    private final HomeService home;

    public HomeController(HomeService home) {
        this.home = home;
    }

    @GetMapping({"", "/", "/Index", "/index"})
    public String index(Model model) {
        model.addAttribute("movies", home.getHomeMovies());
        return "home/index";
    }

    @GetMapping({"/Movie", "/movie"})
    public String movie(Model model) {
        var catalog = home.getMovieCatalog();
        model.addAttribute("movies", catalog.featured());
        model.addAttribute("allNowShowingMovies", catalog.nowShowing());
        model.addAttribute("allComingSoonMovies", catalog.comingSoon());
        return "home/movie";
    }

    @GetMapping({"/Details/{id}", "/details/{id}"})
    public String detailsPath(@PathVariable int id, HttpServletRequest request, Model model) {
        return details(id, request, model);
    }

    @GetMapping({"/Details", "/details"})
    public String details(@RequestParam int id, HttpServletRequest request, Model model) {
        var movie = home.getMovieDetails(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("movie", movie);
        model.addAttribute("reviewForm", new MovieReviewFormViewModel());
        model.addAttribute("canReview", home.isActiveUser(currentUserId(request)));
        return "home/details";
    }

    @PostMapping({"/AddReview", "/add-review"})
    public String addReview(@Valid @ModelAttribute("reviewForm") MovieReviewFormViewModel form,
                            BindingResult errors, HttpServletRequest request,
                            RedirectAttributes flash) {
        int movieId = form.getMovieId() == null ? 0 : form.getMovieId();
        if (errors.hasErrors()) {
            flash.addFlashAttribute("alertError",
                    "Vui lòng chọn điểm đánh giá từ 0 đến 5 và nhập nội dung trước khi gửi.");
            return detailsRedirect(movieId);
        }
        var result = home.addReview(currentUserId(request), form);
        if (result.status() == HomeService.ReviewStatus.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (result.status() == HomeService.ReviewStatus.SUCCESS) {
            flash.addFlashAttribute("alertSuccess", result.message());
        } else {
            flash.addFlashAttribute("alertError", result.message());
        }
        return detailsRedirect(result.movieId());
    }

    @GetMapping({"/TicketPricing", "/ticket-pricing"})
    public String ticketPricing(Model model) {
        model.addAttribute("ticketPricing", home.getTicketPricing());
        return "home/ticket-pricing";
    }

    private Integer currentUserId(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && session.getAttribute("UserID") instanceof Integer id ? id : null;
    }

    private String detailsRedirect(int movieId) {
        return "redirect:/Home/Details?id=" + movieId;
    }
}
