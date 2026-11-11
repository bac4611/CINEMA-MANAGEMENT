package com.cinema.management.model.viewmodel.staff;
import lombok.*;
@Getter @Setter @NoArgsConstructor
public class StaffSeatViewModel {
    private int seatId; private String seatCode = ""; private String seatNumber = "";
    private int columnSpan = 1; private String seatType = ""; private String seatStatus = "";
    private boolean placeholder;
}

