package com.cinema.management.model.viewmodel.home;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class OfferViewModel {
    private String id = "";
    private String title = "";
    private String slug = "";
    private String summary = "";
    private String description = "";
    private String code;
    private String category = "";
    private String categoryLabel = "";
    private String badge;
    private String discountType = "";
    private BigDecimal discountValue;
    private BigDecimal maximumDiscount;
    private BigDecimal minimumOrder;
    private String displayValue = "";
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean featured;
    private boolean onlineOnly;
    private boolean memberOnly;
    private boolean stackable;
    private List<String> terms = new ArrayList<>();
    private String status = "active";
    private String statusLabel = "";
    private String validityLabel = "";
    private String remainingLabel = "";
    private boolean expiringSoon;

    public boolean isCanApply() {
        return "active".equalsIgnoreCase(status);
    }
}

