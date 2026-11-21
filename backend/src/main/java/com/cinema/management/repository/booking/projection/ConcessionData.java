package com.cinema.management.repository.booking.projection;

import java.math.BigDecimal;

public record ConcessionData(Integer comboId, String name, BigDecimal currentPrice,
                              int quantity, int reservedQuantity, Integer heldQuantity, BigDecimal heldPrice) {
}
