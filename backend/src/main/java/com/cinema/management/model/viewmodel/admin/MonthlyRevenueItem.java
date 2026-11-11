package com.cinema.management.model.viewmodel.admin;
import lombok.*; import java.math.BigDecimal;
@Getter @Setter @NoArgsConstructor
public class MonthlyRevenueItem { private int year; private int month; private String label = ""; private BigDecimal revenue = BigDecimal.ZERO; }

