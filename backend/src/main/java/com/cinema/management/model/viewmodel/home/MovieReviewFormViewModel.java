package com.cinema.management.model.viewmodel.home;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
public class MovieReviewFormViewModel {
    @NotNull private Integer movieId;
    @NotNull(message = "Vui lòng chọn điểm đánh giá.")
    @DecimalMin(value = "0.00", message = "Điểm đánh giá phải từ 0 đến 5.")
    @DecimalMax(value = "5.00", message = "Điểm đánh giá phải từ 0 đến 5.")
    private BigDecimal rating;
    @NotBlank(message = "Vui lòng nhập nội dung đánh giá.")
    @Size(max = 2000, message = "Nội dung đánh giá tối đa 2000 ký tự.")
    private String comment = "";
}

