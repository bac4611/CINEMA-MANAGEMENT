package com.cinema.management.model.viewmodel.booking;

import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Getter @Setter @NoArgsConstructor
public class BookingViewModel {
    private String bookingId = UUID.randomUUID().toString().replace("-", "");
    private int currentStep = 1;
    private Instant bookingExpiresAtUtc;
    private int movieId = 1;
    private Integer showtimeId;
    private String movieTitle = "";
    private String director = "";
    private String cast = "";
    private String language = "";
    private String country = "";
    private String synopsis = "";
    private String ageRating = "";
    private int durationMinutes = 166;
    private String posterUrl = "";
    private String genre = "";
    private String selectedDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_DATE);
    private String selectedTime = "";
    private String cinemaFormat = "";
    private String offerCode;
    private List<String> availableDates = new ArrayList<>();
    private List<String> availableTimes = new ArrayList<>();
    private List<String> availableFormats = new ArrayList<>();
    private List<ShowtimeChoiceViewModel> showtimeChoices = new ArrayList<>();
    private List<String> selectedSeats = new ArrayList<>();
    private List<String> occupiedSeats = new ArrayList<>(List.of("A6", "A7", "B10", "C4", "D8", "D9", "F2", "G9"));
    private BigDecimal standardTicketPrice = new BigDecimal("18.50");
    private BigDecimal vipTicketPrice = BigDecimal.ZERO;
    private BigDecimal sweetboxTicketPrice = new BigDecimal("30.00");
    private BigDecimal convenienceFeePerTicket = new BigDecimal("1.50");
    private BigDecimal taxRate = new BigDecimal("0.08");
    private List<SeatChoiceViewModel> seatChoices = new ArrayList<>();
    private List<ConcessionItemViewModel> concessions = new ArrayList<>();
    private boolean paid;
    private String confirmationNumber;

    public BigDecimal seatPrice(String seatCode) {
        return seatChoices.stream()
                .filter(seat -> seat.getSeatCode().equalsIgnoreCase(seatCode))
                .map(SeatChoiceViewModel::getPrice)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    public BigDecimal getTicketSubtotal() {
        return selectedSeats.stream().map(this::seatPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getConvenienceFee() {
        return convenienceFeePerTicket.multiply(BigDecimal.valueOf(selectedSeats.size()));
    }

    public BigDecimal getConcessionSubtotal() {
        return concessions.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getSelectedQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getPreTaxTotal() {
        return getTicketSubtotal().add(getConvenienceFee()).add(getConcessionSubtotal());
    }

    public BigDecimal getTax() {
        return getPreTaxTotal().multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getGrandTotal() {
        return getPreTaxTotal().add(getTax());
    }
}

