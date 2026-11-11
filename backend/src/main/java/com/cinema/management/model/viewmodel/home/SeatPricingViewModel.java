package com.cinema.management.model.viewmodel.home;
import lombok.*; import java.math.BigDecimal;
@Getter @Setter @NoArgsConstructor
public class SeatPricingViewModel {
    private SeatType seatType; private BigDecimal basePrice = BigDecimal.ZERO;
    private BigDecimal normalDay = BigDecimal.ZERO; private BigDecimal weekendOrHoliday = BigDecimal.ZERO;
}

