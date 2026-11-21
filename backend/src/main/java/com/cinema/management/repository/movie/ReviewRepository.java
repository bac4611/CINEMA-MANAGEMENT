package com.cinema.management.repository.movie;

import com.cinema.management.model.entity.movie.Review;
import com.cinema.management.repository.movie.projection.MovieRatingTotals;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collection;

public interface ReviewRepository extends JpaRepository<Review, Integer> {
    @Query("""
            select new com.cinema.management.repository.movie.projection.MovieRatingTotals(
                r.movie.movieId, sum(r.rating), count(r))
            from Review r
            where r.movie.movieId in :movieIds
              and r.parentReview is null
              and r.status = :status
              and r.rating is not null
            group by r.movie.movieId
            """)
    List<MovieRatingTotals> findRatingTotalsByMovieIds(@Param("movieIds") Collection<Integer> movieIds,
                                                     @Param("status") String status);

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

