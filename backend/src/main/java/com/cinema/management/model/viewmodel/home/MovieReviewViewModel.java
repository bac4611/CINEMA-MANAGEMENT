package com.cinema.management.model.viewmodel.home;
import lombok.*; import java.math.BigDecimal; import java.time.LocalDateTime;
@Getter @Setter @NoArgsConstructor
public class MovieReviewViewModel {
    private int reviewId; private BigDecimal rating = BigDecimal.ZERO; private String comment = "";
    private LocalDateTime createdAt; private String userFullName = ""; private String userAvatarUrl;
}

