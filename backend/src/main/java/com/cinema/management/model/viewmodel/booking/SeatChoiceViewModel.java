package com.cinema.management.model.viewmodel.booking;
import lombok.*; import java.math.BigDecimal;
@Getter @Setter @NoArgsConstructor
public class SeatChoiceViewModel {
    private int seatId; private String seatCode = ""; private String seatType = "";
    private boolean occupied; private boolean selected; private BigDecimal price = BigDecimal.ZERO;
}

