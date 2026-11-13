package com.cinema.management.service.home;

import com.cinema.management.model.entity.movie.*;
import com.cinema.management.model.viewmodel.home.*;
import com.cinema.management.model.viewmodel.booking.ShowtimeChoiceViewModel;
import com.cinema.management.repository.account.UserRepository;
import com.cinema.management.repository.movie.*;
import com.cinema.management.repository.theater.ShowtimeRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DefaultHomeService implements HomeService {
    private static final String VISIBLE = "Visible";
    private static final String FORMAT_MARKER = "\n[COSMOS_FORMATS:";

    private final MovieRepository movies;
    private final MovieGenreRepository movieGenres;
    private final MovieCastRepository movieCasts;
    private final MovieDirectorRepository movieDirectors;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final ShowtimeRepository showtimes;

    public DefaultHomeService(MovieRepository movies, MovieGenreRepository movieGenres,
                              MovieCastRepository movieCasts, MovieDirectorRepository movieDirectors,
                              ReviewRepository reviews, UserRepository users,
                              ShowtimeRepository showtimes) {
        this.movies = movies;
        this.movieGenres = movieGenres;
        this.movieCasts = movieCasts;
        this.movieDirectors = movieDirectors;
        this.reviews = reviews;
        this.users = users;
        this.showtimes = showtimes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovieViewModel> getHomeMovies() {
        List<MovieViewModel> result = movies.findAll().stream().map(this::mapMovie).toList();
        applyReviewSummaries(result);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public MovieCatalog getMovieCatalog() {
        LocalDate today = LocalDate.now();
        List<Movie> all = new ArrayList<>(movies.findAll());
        List<Movie> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled);

        List<MovieViewModel> featured = shuffled.stream().limit(4).map(this::mapMovie).toList();
        List<MovieViewModel> nowShowing = all.stream()
                .filter(movie -> movie.getReleaseDate() != null && !movie.getReleaseDate().isAfter(today))
                .sorted(Comparator.comparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapMovie).toList();
        List<MovieViewModel> comingSoon = all.stream()
                .filter(movie -> movie.getReleaseDate() != null && movie.getReleaseDate().isAfter(today))
                .sorted(Comparator.comparing(Movie::getReleaseDate)
                        .thenComparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapMovie).toList();

        applyReviewSummaries(featured);
        applyReviewSummaries(nowShowing);
        applyReviewSummaries(comingSoon);
        return new MovieCatalog(featured,
                nowShowing.isEmpty() ? featured.stream()
                        .sorted(Comparator.comparing(MovieViewModel::getTitle, String.CASE_INSENSITIVE_ORDER)).toList()
                        : nowShowing,
                comingSoon);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MovieViewModel> getMovieDetails(int movieId) {
        return movies.findById(movieId).map(movie -> {
            MovieViewModel model = mapMovie(movie);
            applyReviewSummaries(List.of(model));
            model.setReviews(reviews
                    .findByMovieMovieIdAndParentReviewIsNullAndStatusAndRatingIsNotNullOrderByCreatedAtDesc(
                            movieId, VISIBLE)
                    .stream().map(this::mapReview).toList());
            return model;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveUser(Integer userId) {
        return userId != null && users.findById(userId).map(user -> user.isStatus()).orElse(false);
    }

    @Override
    @Transactional
    public ReviewResult addReview(Integer userId, MovieReviewFormViewModel form) {
        int movieId = form == null || form.getMovieId() == null ? 0 : form.getMovieId();
        if (!isActiveUser(userId)) {
            return new ReviewResult(ReviewStatus.UNAUTHORIZED, movieId,
                    "Vui lòng đăng nhập để gửi đánh giá phim.");
        }
        Movie movie = movies.findById(movieId).orElse(null);
        if (movie == null) {
            return new ReviewResult(ReviewStatus.NOT_FOUND, movieId, "Không tìm thấy phim.");
        }
        String comment = form.getComment() == null ? "" : form.getComment().trim();
        BigDecimal rating = form.getRating();
        if (comment.isBlank() || comment.length() > 2000 || rating == null
                || rating.compareTo(BigDecimal.ZERO) < 0
                || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            return new ReviewResult(ReviewStatus.INVALID, movieId,
                    "Vui lòng chọn điểm đánh giá từ 0 đến 5 và nhập nội dung trước khi gửi.");
        }

        Review review = new Review();
        review.setMovie(movie);
        review.setUser(users.getReferenceById(userId));
        review.setContent(comment);
        review.setRating(rating.setScale(2, RoundingMode.HALF_UP));
        review.setCreatedAt(LocalDateTime.now(java.time.Clock.systemUTC()));
        review.setStatus(VISIBLE);
        try {
            reviews.saveAndFlush(review);
            return new ReviewResult(ReviewStatus.SUCCESS, movieId, "Đánh giá của bạn đã được gửi.");
        } catch (DataAccessException exception) {
            return new ReviewResult(ReviewStatus.PERSISTENCE_ERROR, movieId,
                    "Chưa thể lưu đánh giá. Vui lòng thử lại.");
        }
    }

    @Override
    public TicketPricingViewModel getTicketPricing() {
        TicketPricingViewModel result = new TicketPricingViewModel();
        result.setMovieFormats(List.of(
                pricing("2D", "Phim 2D", 65_000, 85_000),
                pricing("IMAX", "Phim IMAX", 135_000, 165_000)));
        result.setFootnotes(List.of(
                "Giá vé định dạng IMAX phụ thu thêm 50.000đ tùy hạng ghế.",
                "Sweetbox là giá vé dành cho 2 người.",
                "Trẻ em dưới 1m3 được giảm 20.000đ/vé khi mua tại quầy."));
        return result;
    }

    private MoviePricingViewModel pricing(String id, String label, long regular, long weekend) {
        MoviePricingViewModel format = new MoviePricingViewModel();
        format.setId(id);
        format.setTabLabel(label);
        format.setSeatPrices(List.of(
                seatPrice(SeatType.STANDARD, regular, weekend),
                seatPrice(SeatType.VIP, regular * 3 / 2, weekend * 3 / 2),
                seatPrice(SeatType.SWEETBOX, regular * 5 / 2, weekend * 5 / 2)));
        return format;
    }

    private SeatPricingViewModel seatPrice(SeatType type, long normal, long weekend) {
        SeatPricingViewModel price = new SeatPricingViewModel();
        price.setSeatType(type);
        price.setBasePrice(BigDecimal.valueOf(normal));
        price.setNormalDay(BigDecimal.valueOf(normal));
        price.setWeekendOrHoliday(BigDecimal.valueOf(weekend));
        return price;
    }

    private MovieViewModel mapMovie(Movie movie) {
        MovieViewModel model = new MovieViewModel();
        model.setMovieId(movie.getMovieId());
        model.setTitle(movie.getTitle());
        model.setDuration(movie.getDuration());
        model.setPosterUrl(movie.getPosterUrl());
        model.setSynopsis(movie.getSynopsis());
        model.setReleaseDate(movie.getReleaseDate());
        model.setAgeRating(movie.getAgeRating());
        model.setTrailer(cleanTrailer(movie.getTrailer()));
        model.setAvailableFormats(extractFormats(movie.getTrailer()));
        if (movie.getLanguage() != null) {
            model.setLanguageId(movie.getLanguage().getLanguageId());
            model.setLanguageName(movie.getLanguage().getLanguageName());
        }
        if (movie.getCountry() != null) {
            model.setCountryId(movie.getCountry().getCountryId());
            model.setCountryName(movie.getCountry().getCountryName());
        }
        model.setGenre(movieGenres.findByMovieId(movie.getMovieId()).stream()
                .map(MovieGenre::getGenre).filter(Objects::nonNull).map(Genre::getName)
                .collect(Collectors.joining(", ")));
        model.setMovieDirector(movieDirectors.findByMovieId(movie.getMovieId()).stream()
                .map(MovieDirector::getPerson).filter(Objects::nonNull).map(Person::getFullName)
                .collect(Collectors.joining(", ")));
        model.setMovieCast(movieCasts.findByMovieId(movie.getMovieId()).stream()
                .map(MovieCast::getPerson).filter(Objects::nonNull).map(Person::getFullName)
                .collect(Collectors.joining(", ")));
        model.setShowtimes(showtimes.findByMovieMovieIdOrderByDateAscStartTimeAsc(movie.getMovieId())
                .stream().map(showtime -> {
                    ShowtimeChoiceViewModel choice = new ShowtimeChoiceViewModel();
                    choice.setShowtimeId(showtime.getShowtimeId());
                    choice.setDate(showtime.getDate().toString());
                    choice.setTime(showtime.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                    String roomName = showtime.getRoom() == null ? "" : showtime.getRoom().getRoomName();
                    choice.setRoomName(roomName);
                    choice.setFormat(roomName.toUpperCase(Locale.ROOT).contains("IMAX") ? "IMAX 2D" : "2D");
                    return choice;
                }).toList());
        return model;
    }

    private MovieReviewViewModel mapReview(Review review) {
        MovieReviewViewModel model = new MovieReviewViewModel();
        model.setReviewId(review.getReviewId());
        model.setRating(review.getRating());
        model.setComment(review.getContent());
        model.setCreatedAt(review.getCreatedAt());
        model.setUserFullName(review.getUser() == null ? "COSMOS member" : review.getUser().getFullName());
        return model;
    }

    private void applyReviewSummaries(List<MovieViewModel> target) {
        if (target.isEmpty()) return;
        Map<Integer, List<Review>> grouped = reviews
                .findByMovieMovieIdInAndParentReviewIsNullAndStatusAndRatingIsNotNull(
                        target.stream().map(MovieViewModel::getMovieId).distinct().toList(), VISIBLE)
                .stream().collect(Collectors.groupingBy(review -> review.getMovie().getMovieId()));
        target.forEach(movie -> {
            List<Review> ratings = grouped.getOrDefault(movie.getMovieId(), List.of());
            BigDecimal average = ratings.isEmpty() ? BigDecimal.ZERO : ratings.stream()
                    .map(Review::getRating).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(ratings.size()), 2, RoundingMode.HALF_UP);
            MovieReviewSummaryViewModel summary = new MovieReviewSummaryViewModel();
            summary.setAverageRating(average);
            summary.setTotalRatings(ratings.size());
            movie.setAverageRating(average);
            movie.setReviewCount(ratings.size());
            movie.setReviewSummary(summary);
        });
    }

    private String cleanTrailer(String stored) {
        if (stored == null || stored.isBlank()) return "";
        int marker = stored.toUpperCase(Locale.ROOT).indexOf(FORMAT_MARKER.trim().toUpperCase(Locale.ROOT));
        return (marker < 0 ? stored : stored.substring(0, marker)).trim();
    }

    private String extractFormats(String stored) {
        if (stored == null) return "2D";
        String upper = stored.toUpperCase(Locale.ROOT);
        int marker = upper.indexOf(FORMAT_MARKER.trim().toUpperCase(Locale.ROOT));
        if (marker < 0) return "2D";
        int start = marker + FORMAT_MARKER.trim().length();
        int end = stored.indexOf(']', start);
        if (end <= start) return "2D";
        List<String> formats = Arrays.stream(stored.substring(start, end).split(","))
                .map(String::trim).map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> value.equals("2D") || value.equals("IMAX")).distinct().toList();
        return formats.isEmpty() ? "2D" : String.join(",", formats);
    }
}
