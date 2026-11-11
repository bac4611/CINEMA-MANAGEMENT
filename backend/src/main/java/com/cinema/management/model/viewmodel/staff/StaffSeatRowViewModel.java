package com.cinema.management.model.viewmodel.staff;
import lombok.*; import java.util.ArrayList; import java.util.List;
@Getter @Setter @NoArgsConstructor
public class StaffSeatRowViewModel {
    private String rowLabel = ""; private List<StaffSeatViewModel> seats = new ArrayList<>();
}

