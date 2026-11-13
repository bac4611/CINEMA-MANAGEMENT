package com.cinema.management.service.chatbot;

import com.cinema.management.model.dto.request.GenreRecommendationInput;
import com.cinema.management.model.dto.response.ChatbotMovieResult;
import com.cinema.management.model.dto.response.ChatbotResponse;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.movie.MovieGenre;
import com.cinema.management.model.entity.theater.Showtime;
import com.cinema.management.repository.movie.MovieRepository;
import com.cinema.management.service.recommendation.GenreRecommendationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DefaultChatbotService implements ChatbotService {
    private static final int RECOMMENDATION_LIMIT = 5;
    private static final DateTimeFormatter SHOWTIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MovieRepository movies;
    private final GenreRecommendationService genres;

    public DefaultChatbotService(MovieRepository movies, GenreRecommendationService genres) {
        this.movies = movies;
        this.genres = genres;
    }

    @Override
    @Transactional(readOnly = true)
    public ChatbotResponse recommend(GenreRecommendationInput input) {
        var prediction = genres.predict(input);
        String genreName = prediction == null ? null : prediction.genreName();
        List<ChatbotMovieResult> selected = genreName == null || genreName.isBlank()
                ? List.of()
                : recommendMovies(movies.findAllForChatbot(), input, genreName);

        return new ChatbotResponse(
                "recommend_by_mood",
                input.mood(),
                prediction == null ? null : prediction.genreCode(),
                genreName,
                composeReply(genreName, selected),
                selected);
    }

    private List<ChatbotMovieResult> recommendMovies(List<Movie> candidates,
                                                      GenreRecommendationInput input,
                                                      String genreName) {
        String normalizedGenre = normalizeText(genreName);
        return candidates.stream()
                .filter(movie -> movieGenreNames(movie).stream()
                        .anyMatch(name -> normalizeText(name).equals(normalizedGenre)))
                .map(movie -> new ScoredMovie(movie, score(movie, input, normalizedGenre)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredMovie::score).reversed()
                        .thenComparing(item -> nearestShowtime(item.movie()),
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(item -> text(item.movie().getTitle())))
                .limit(RECOMMENDATION_LIMIT)
                .map(this::toResult)
                .toList();
    }

    private double score(Movie movie, GenreRecommendationInput input, String normalizedGenre) {
        double score = 5;
        String rating = text(movie.getAgeRating());
        String synopsis = normalizeText(movie.getSynopsis());

        if (rating.equalsIgnoreCase(text(input.ageRating()))) {
            score += 3;
        } else if ("P".equals(input.ageRating()) && ("T16".equals(rating) || "T18".equals(rating))) {
            score -= 5;
        }
        if ("tre_em".equals(input.companion()) || "gia_dinh".equals(input.companion())) {
            if ("P".equalsIgnoreCase(rating)) score += 3;
            if ("T18".equalsIgnoreCase(rating)) score -= 4;
        }
        if ("gay_can".equals(input.intensity())
                && containsAny(synopsis, "chien dau", "sinh ton", "rung ron", "bi an", "vu tru")) score += 2;
        if ("nhe_nhang".equals(input.intensity())
                && containsAny(synopsis, "tinh ban", "dang yeu", "gia dinh", "cau chuyen")) score += 2;

        if ("khoa hoc vien tuong".equals(normalizedGenre)
                && containsAny(synopsis, "tuong lai", "vu tru", "sieu anh hung", "chien binh")) score += 3;
        if ("hanh dong".equals(normalizedGenre)
                && containsAny(synopsis, "chien dau", "tran chien", "bao ve", "cong ly", "sinh ton")) score += 3;
        if ("hoat hinh".equals(normalizedGenre)
                && containsAny(synopsis, "doraemon", "do choi", "dang yeu", "tinh ban", "nobita")) score += 3;
        if ("tinh cam".equals(normalizedGenre)
                && containsAny(synopsis, "cau chuyen", "hoan doi", "co gai", "chang trai")) score += 3;
        if ("cam dong".equals(normalizedGenre)
                && containsAny(synopsis, "tinh ban", "dang yeu", "cau chuyen", "gia dinh")) score += 3;
        if (!showtimes(movie).isEmpty()) score += 1;
        return score;
    }

    private ChatbotMovieResult toResult(ScoredMovie item) {
        Movie movie = item.movie();
        LocalDateTime nearest = nearestShowtime(movie);
        return new ChatbotMovieResult(
                movie.getMovieId(),
                text(movie.getTitle()),
                String.join(", ", movieGenreNames(movie)),
                text(movie.getAgeRating()),
                movie.getDuration(),
                text(movie.getSynopsis()),
                nearest == null ? null : nearest.format(SHOWTIME_FORMAT),
                showtimes(movie).size(),
                Math.round(item.score() * 100d) / 100d);
    }

    private static List<String> movieGenreNames(Movie movie) {
        return Optional.ofNullable(movie.getMovieGenres()).orElseGet(List::of).stream()
                .map(MovieGenre::getGenre)
                .filter(Objects::nonNull)
                .map(genre -> genre.getName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    private static List<LocalDateTime> showtimes(Movie movie) {
        return Optional.ofNullable(movie.getShowtimes()).orElseGet(List::of).stream()
                .map(Showtime::getStartTime)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    private static LocalDateTime nearestShowtime(Movie movie) {
        List<LocalDateTime> values = showtimes(movie);
        if (values.isEmpty()) return null;
        LocalDateTime now = LocalDateTime.now();
        return values.stream().filter(value -> !value.isBefore(now)).findFirst().orElse(values.getFirst());
    }

    private static String composeReply(String genreName, List<ChatbotMovieResult> movies) {
        if (genreName == null || genreName.isBlank()) {
            return "Mình chưa dự đoán được thể loại phù hợp. Bạn thử chọn lại các tiêu chí nhé.";
        }
        if (movies.isEmpty()) {
            return "Hệ thống dự đoán thể loại " + genreName
                    + ", nhưng hiện chưa có phim thuộc thể loại này trong dữ liệu lịch chiếu.";
        }
        return "Thể loại phù hợp: " + genreName + ". Danh sách phim phù hợp nằm bên dưới.";
    }

    private static boolean containsAny(String source, String... keywords) {
        return Arrays.stream(keywords).anyMatch(source::contains);
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd');
        return Normalizer.normalize(normalized, Normalizer.Form.NFC);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private record ScoredMovie(Movie movie, double score) {}
}
