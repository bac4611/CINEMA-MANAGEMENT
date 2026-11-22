package com.cinema.management.repository.theater.projection;

import java.math.BigDecimal;

// A null seatId identifies a pricing row; seat rows and the small pricing table
// travel in one result without multiplying every seat by every price category.
public record SeatLayoutData(Integer seatId, String seatCode, String seatType, BigDecimal multiplier) {
}
