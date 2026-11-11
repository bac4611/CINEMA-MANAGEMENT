package com.cinema.management.model.viewmodel.staff;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
public class StaffReservationViewModel {
    private int bookingId; private int roomId; private String roomName = "";
    private String customerName = ""; private String customerEmail = ""; private String movieTitle = "";
    private LocalDateTime bookingDate; private LocalDateTime showtimeStart; private String seatCodes = "";
    private int ticketCount; private BigDecimal totalAmount = BigDecimal.ZERO; private String status = "";
}

