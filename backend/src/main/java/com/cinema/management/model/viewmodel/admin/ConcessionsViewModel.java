package com.cinema.management.model.viewmodel.admin;

import com.cinema.management.model.entity.booking.Combo;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class ConcessionsViewModel {
    private List<Combo> items = new ArrayList<>();
    private Combo form = new Combo();
    private BigDecimal monthlyRevenue = BigDecimal.ZERO;
    private int totalTransactions;
    private BigDecimal averageOrderValue = BigDecimal.ZERO;
}

