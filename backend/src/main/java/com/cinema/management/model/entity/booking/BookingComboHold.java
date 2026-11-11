package com.cinema.management.model.entity.booking;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "BookingComboHolds")
@IdClass(BookingComboHold.Key.class)
@Getter @Setter @NoArgsConstructor
public class BookingComboHold {
    @Id @Column(name = "BookingHoldID") private UUID bookingHoldId;
    @Id @Column(name = "ComboID") private Integer comboId;
    private int quantity;
    @Column(precision = 10, scale = 2) private BigDecimal unitPrice = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingHoldID", insertable = false, updatable = false)
    private BookingHold bookingHold;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ComboID", insertable = false, updatable = false)
    private Combo combo;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID bookingHoldId;
        private Integer comboId;
    }
}

