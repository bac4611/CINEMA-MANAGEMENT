package com.cinema.management.model.viewmodel.home;
import lombok.*;
@Getter @Setter @NoArgsConstructor
public class OfferQuickBookingShowtimeViewModel {
    private int showtimeId; private String date = ""; private String dateLabel = "";
    private String time = ""; private String format = "2D"; private String roomName = "";
}

