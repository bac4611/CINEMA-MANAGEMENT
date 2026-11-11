package com.cinema.management.model.viewmodel.booking;
import lombok.*;
@Getter @Setter @NoArgsConstructor
public class ShowtimeChoiceViewModel {
    private int showtimeId; private String date = ""; private String time = "";
    private String format = ""; private String roomName = "";
}

