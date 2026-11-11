package com.cinema.management.model.viewmodel.booking;
import lombok.*; import java.time.LocalDate;
@Getter @Setter @NoArgsConstructor
public class BookingScheduleDateOption {
    private LocalDate date; private String value = ""; private String dateLabel = "";
    private String weekdayLabel = ""; private boolean today; private boolean selected;
}

