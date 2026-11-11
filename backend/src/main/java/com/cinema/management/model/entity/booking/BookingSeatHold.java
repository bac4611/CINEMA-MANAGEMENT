package com.cinema.management.model.entity.booking;

import com.cinema.management.model.entity.theater.Seat;
import com.cinema.management.model.entity.theater.Showtime;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "BookingSeatHolds")
@IdClass(BookingSeatHold.Key.class)
@Getter @Setter @NoArgsConstructor
public class BookingSeatHold {
    @Id @Column(name = "ShowtimeID") private Integer showtimeId;
    @Id @Column(name = "SeatID") private Integer seatId;
    @Column(name = "BookingHoldID", nullable = false) private UUID bookingHoldId;
    @Column(precision = 10, scale = 2) private BigDecimal unitPrice = BigDecimal.ZERO;
    private Instant heldAtUtc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingHoldID", insertable = false, updatable = false)
    private BookingHold bookingHold;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ShowtimeID", insertable = false, updatable = false)
    private Showtime showtime;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SeatID", insertable = false, updatable = false)
    private Seat seat;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Key implements Serializable {
        private Integer showtimeId;
        private Integer seatId;
    }
}

