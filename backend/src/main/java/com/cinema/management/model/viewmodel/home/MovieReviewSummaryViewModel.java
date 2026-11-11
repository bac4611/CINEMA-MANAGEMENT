package com.cinema.management.model.viewmodel.home;
import lombok.*; import java.math.BigDecimal;
@Getter @Setter @NoArgsConstructor
public class MovieReviewSummaryViewModel {
    private BigDecimal averageRating = BigDecimal.ZERO; private int totalRatings;
}

