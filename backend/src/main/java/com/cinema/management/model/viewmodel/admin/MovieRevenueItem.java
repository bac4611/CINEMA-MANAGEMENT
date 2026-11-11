package com.cinema.management.model.viewmodel.admin;
import lombok.*; import java.math.BigDecimal;
@Getter @Setter @NoArgsConstructor
public class MovieRevenueItem { private int movieId; private String movieTitle = ""; private BigDecimal revenue = BigDecimal.ZERO; }

