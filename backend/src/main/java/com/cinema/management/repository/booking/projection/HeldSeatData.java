package com.cinema.management.repository.booking.projection;

import java.math.BigDecimal;

public record HeldSeatData(Integer seatId, String seatCode, String seatType, BigDecimal unitPrice) {
}
