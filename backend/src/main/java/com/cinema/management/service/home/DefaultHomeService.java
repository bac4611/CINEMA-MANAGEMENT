package com.cinema.management.service.home;

import com.cinema.management.model.entity.movie.*;
import com.cinema.management.model.viewmodel.home.*;
import com.cinema.management.model.viewmodel.booking.ShowtimeChoiceViewModel;
import com.cinema.management.repository.account.UserRepository;
import com.cinema.management.repository.movie.*;
import com.cinema.management.repository.movie.projection.*;
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

@Service
public class DefaultHomeService implements HomeService {
    private static final String VISIBLE = "Visible";
    private static final String FORMAT_MARKER = "\n[COSMOS_FORMATS:";
    // Keep IN lists comfortably below SQL Server's 2,100 parameter limit.
    private static final int CATALOG_BATCH_SIZE = 1000;

    private final MovieRepository movies;
    private final MovieGenreRepository movieGenres;
    private final MovieInformationCache movieInformation;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final ShowtimeRepository showtimes;

    public DefaultHomeService(MovieRepository movies, MovieGenreRepository movieGenres,
                              MovieInformationCache movieInformation,
                              ReviewRepository reviews, UserRepository users,
                              ShowtimeRepository showtimes) {
        this.movies = movies;
        this.movieGenres = movieGenres;
        this.movieInformation = movieInformation;
        this.reviews = reviews;
        this.users = users;
        this.showtimes = showtimes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovieViewModel> getHomeMovies() {
        return loadMovieCards();
    }

    @Override
    @Transactional(readOnly = true)
    public MovieCatalog getMovieCatalog() {
        LocalDate today = LocalDate.now();
        List<MovieViewModel> all = loadMovieCards();
        List<MovieViewModel> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled);

        List<MovieViewModel> featured = shuffled.stream().limit(4).toList();
        List<MovieViewModel> nowShowing = all.stream()
                .filter(movie -> movie.getReleaseDate() != null && !movie.getReleaseDate().isAfter(today))
                .sorted(Comparator.comparing(MovieViewModel::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<MovieViewModel> comingSoon = all.stream()
                .filter(movie -> movie.getReleaseDate() != null && movie.getReleaseDate().isAfter(today))
                .sorted(Comparator.comparing(MovieViewModel::getReleaseDate)
                        .thenComparing(MovieViewModel::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new MovieCatalog(featured,
                nowShowing.isEmpty() ? featured.stream()
                        .sorted(Comparator.comparing(MovieViewModel::getTitle, String.CASE_INSENSITIVE_ORDER)).toList()
                        : nowShowing,
                comingSoon);
    }

    private List<MovieViewModel> loadMovieCards() {
        List<MovieCardData> cards = movies.findAllCardData();
        if (cards.isEmpty()) return List.of();
        List<Integer> ids = cards.stream().map(MovieCardData::movieId).toList();
        Map<Integer, List<String>> genresByMovie = new HashMap<>();
        Map<Integer, MovieRatingTotals> ratingsByMovie = new HashMap<>();
        for (int offset = 0; offset < ids.size(); offset += CATALOG_BATCH_SIZE) {
            List<Integer> batch = ids.subList(offset, Math.min(offset + CATALOG_BATCH_SIZE, ids.size()));
            movieGenres.findNamesByMovieIds(batch).forEach(genre -> genresByMovie
                    .computeIfAbsent(genre.movieId(), ignored -> new ArrayList<>()).add(genre.name()));
            reviews.findRatingTotalsByMovieIds(batch, VISIBLE)
                    .forEach(rating -> ratingsByMovie.put(rating.movieId(), rating));
        }
        return cards.stream().map(card -> {
            MovieViewModel model = new MovieViewModel();
            model.setMovieId(card.movieId());
            model.setTitle(card.title());
            model.setDuration(card.duration());
            model.setPosterUrl(card.posterUrl());
            model.setSynopsis(card.synopsis());
            model.setReleaseDate(card.releaseDate());
            model.setAgeRating(card.ageRating());
            model.setTrailer(cleanTrailer(card.trailer()));
            model.setAvailableFormats(extractFormats(card.trailer()));
            model.setGenre(String.join(", ", genresByMovie.getOrDefault(card.movieId(), List.of())));
            MovieRatingTotals rating = ratingsByMovie.get(card.movieId());
            int count = rating == null ? 0 : Math.toIntExact(rating.ratingCount());
            BigDecimal average = count == 0 ? BigDecimal.ZERO : rating.totalRating()
                    .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            applyReviewSummary(model, average, count);
            return model;
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MovieViewModel> getMovieDetails(int movieId) {
        return movieInformation.get(movieId).map(movie -> {
            MovieViewModel model = mapMovie(movie);
            List<Review> visibleReviews = reviews
                    .findByMovieMovieIdAndParentReviewIsNullAndStatusAndRatingIsNotNullOrderByCreatedAtDesc(
                            movieId, VISIBLE);
            BigDecimal average = visibleReviews.isEmpty() ? BigDecimal.ZERO : visibleReviews.stream()
                    .map(Review::getRating).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(visibleReviews.size()), 2, RoundingMode.HALF_UP);
            applyReviewSummary(model, average, visibleReviews.size());
            model.setReviews(visibleReviews.stream().map(this::mapReview).toList());
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

    private MovieViewModel mapMovie(MovieInformation movie) {
        MovieViewModel model = new MovieViewModel();
        model.setMovieId(movie.movieId());
        model.setTitle(movie.title());
        model.setDuration(movie.duration());
        model.setPosterUrl(movie.posterUrl());
        model.setSynopsis(movie.synopsis());
        model.setReleaseDate(movie.releaseDate());
        model.setAgeRating(movie.ageRating());
        model.setTrailer(cleanTrailer(movie.trailer()));
        model.setAvailableFormats(extractFormats(movie.trailer()));
        model.setLanguageId(movie.languageId());
        model.setLanguageName(movie.languageName());
        model.setCountryId(movie.countryId());
        model.setCountryName(movie.countryName());
        model.setGenre(movie.genre());
        model.setMovieDirector(movie.director());
        model.setMovieCast(movie.cast());
        LocalDate today = LocalDate.now();
        model.setShowtimes(showtimes.findMovieSchedule(movie.movieId(), today, today.plusDays(7))
                .stream().map(showtime -> {
                    ShowtimeChoiceViewModel choice = new ShowtimeChoiceViewModel();
                    choice.setShowtimeId(showtime.showtimeId());
                    choice.setDate(showtime.date().toString());
                    choice.setTime(showtime.startTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                    String roomName = showtime.roomName() == null ? "" : showtime.roomName();
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

    private void applyReviewSummary(MovieViewModel movie, BigDecimal average, int count) {
        MovieReviewSummaryViewModel summary = new MovieReviewSummaryViewModel();
        summary.setAverageRating(average);
        summary.setTotalRatings(count);
        movie.setAverageRating(average);
        movie.setReviewCount(count);
        movie.setReviewSummary(summary);
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
