package com.cinema.management.model.viewmodel.staff;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class ScreeningRoomStateViewModel {
    private int roomId;
    private Integer selectedShowtimeId;
    private List<ShowtimeItem> showtimes = new ArrayList<>();
    private List<SeatRow> rows = new ArrayList<>();
    private SeatCounts counts = new SeatCounts();
    private List<Reservation> reservations = new ArrayList<>();

    @Getter @Setter @NoArgsConstructor
    public static class ShowtimeItem {
        private int showtimeId; private String movieTitle = "";
        private LocalDateTime startTime; private LocalDateTime endTime;
        public String getLabel() { return startTime == null ? movieTitle
                : startTime.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")) + " - " + movieTitle; }
    }

    @Getter @Setter @NoArgsConstructor
    public static class SeatRow { private String rowLabel = ""; private List<SeatItem> seats = new ArrayList<>(); }

    @Getter @Setter @NoArgsConstructor
    public static class SeatItem {
        private int seatId; private String seatCode = ""; private String seatNumber = "";
        private String seatType = ""; private String reservationState = "available";
        private int columnSpan = 1; private boolean placeholder;
    }

    @Getter @Setter @NoArgsConstructor
    public static class SeatCounts {
        private int total; private int regular; private int vip; private int couple;
        private int reserved; private int pending; private int available;
    }

    @Getter @Setter @NoArgsConstructor
    public static class Reservation {
        private int bookingId; private String customerName = ""; private String customerEmail = "";
        private LocalDateTime bookingDate; private String status = ""; private List<String> seatCodes = new ArrayList<>();
        private int ticketCount; private BigDecimal totalAmount = BigDecimal.ZERO;
    }
}
