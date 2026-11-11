package com.cinema.management.model.entity.booking;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "BookingCombos")
@IdClass(BookingCombo.Key.class)
@Getter @Setter @NoArgsConstructor
public class BookingCombo {
    @Id @Column(name = "BookingID") private Integer bookingId;
    @Id @Column(name = "ComboID") private Integer comboId;
    @Min(1) @Max(10) private int quantity;
    @Column(precision = 10, scale = 2) private BigDecimal unitPrice = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingID", insertable = false, updatable = false)
    private Booking booking;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ComboID", insertable = false, updatable = false)
    private Combo combo;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Key implements Serializable {
        private Integer bookingId;
        private Integer comboId;
    }
}

