package com.cinema.management.model.viewmodel.staff;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class StaffHallViewModel {
    private int roomId; private String roomName = ""; private int seatCount;
    private int regularCount; private int vipCount; private int coupleCount;
    private int orderedCount; private int reservationCount; private int columnCount;
    private List<StaffSeatRowViewModel> rows = new ArrayList<>();
    private List<StaffReservationViewModel> reservations = new ArrayList<>();
}

