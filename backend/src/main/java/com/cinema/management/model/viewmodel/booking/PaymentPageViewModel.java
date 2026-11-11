package com.cinema.management.model.viewmodel.booking;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class PaymentPageViewModel {
    private String paymentReference = "";
    private String movieTitle = "";
    private String cinemaFormat = "";
    private String selectedDate = "";
    private String selectedTime = "";
    private List<String> selectedSeats = new ArrayList<>();
    private List<ConcessionItemViewModel> concessions = new ArrayList<>();
    private BigDecimal ticketSubtotal = BigDecimal.ZERO;
    private BigDecimal concessionSubtotal = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private String qrImageUrl = "";
    private Instant expiresAtUtc;
    private boolean showDevelopmentPaymentButton;
}

