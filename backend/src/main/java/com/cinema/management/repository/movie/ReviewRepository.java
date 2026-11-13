package com.cinema.management.repository.movie;

import com.cinema.management.model.entity.movie.Review;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collection;

public interface ReviewRepository extends JpaRepository<Review, Integer> {
    List<Review> findByMovieMovieIdAndStatusOrderByCreatedAtDesc(
            Integer movieId, String status);
    List<Review> findByUserUserIdOrderByCreatedAtDesc(Integer userId);

    @Query("""
            select coalesce(avg(r.rating), 0)
            from Review r
            where r.movie.movieId = :movieId
              and r.status = 'Visible'
              and r.rating is not null
            """)
    BigDecimal findAverageRating(@Param("movieId") Integer movieId);

    long countByMovieMovieIdAndStatusAndRatingIsNotNull(
            Integer movieId, String status);

    @EntityGraph(attributePaths = "user")
    List<Review> findByMovieMovieIdAndParentReviewIsNullAndStatusAndRatingIsNotNullOrderByCreatedAtDesc(
            Integer movieId, String status);

    List<Review> findByMovieMovieIdInAndParentReviewIsNullAndStatusAndRatingIsNotNull(
            Collection<Integer> movieIds, String status);
}

