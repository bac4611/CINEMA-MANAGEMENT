package com.cinema.management.model.viewmodel.booking;
import lombok.*;
@Getter @Setter @NoArgsConstructor
public class BookingScheduleShowtimeViewModel {
    private int showtimeId; private int movieId; private String date = ""; private String dateLabel = "";
    private String time = ""; private String format = ""; private String roomName = "";
    private int remainingSeats; private int totalSeats; private boolean late;
    private boolean lowAvailability; private boolean soldOut; private String availabilityLabel = "";
}

