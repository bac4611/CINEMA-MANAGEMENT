package com.cinema.management.model.viewmodel.staff;
import lombok.*;
@Getter @Setter @NoArgsConstructor
public class StaffRoomSummaryViewModel {
    private int roomId; private String roomName = ""; private int seatCount;
    private String displayType = "STANDARD"; private String tagCssClass = "tag-standard";
}

