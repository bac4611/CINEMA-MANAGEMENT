package com.cinema.management.repository.booking.projection;

import java.math.BigDecimal;

public record HeldComboData(Integer comboId, String name, BigDecimal unitPrice, int quantity) {
}
