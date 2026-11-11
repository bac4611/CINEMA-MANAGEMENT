package com.cinema.management.model.viewmodel.admin;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class AdminAnalyticsViewModel {
    private String selectedMonth;
    private Integer selectedMovieId;
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    private int totalTicketsSold;
    private int confirmedBookings;
    private List<AnalyticsMonthOption> months = new ArrayList<>();
    private List<AnalyticsMovieOption> movies = new ArrayList<>();
    private List<MonthlyRevenueItem> revenueByMonth = new ArrayList<>();
    private List<MonthlyTicketItem> ticketsByMonth = new ArrayList<>();
    private List<MovieRevenueItem> revenueByMovie = new ArrayList<>();

    public BigDecimal getAverageRevenuePerTicket() {
        return totalTicketsSold == 0 ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(totalTicketsSold), 2, java.math.RoundingMode.HALF_UP);
    }
}

