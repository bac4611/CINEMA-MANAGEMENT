package com.cinema.management.repository.movie.projection;

import java.math.BigDecimal;

/** SUM rather than AVG keeps the existing BigDecimal/HALF_UP rounding in Java. */
public record MovieRatingTotals(Integer movieId, BigDecimal totalRating, Long ratingCount) {
}
