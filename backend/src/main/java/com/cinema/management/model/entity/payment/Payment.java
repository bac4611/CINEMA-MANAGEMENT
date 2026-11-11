package com.cinema.management.model.entity.payment;

import com.cinema.management.model.entity.booking.Booking;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Payments")
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PaymentID")
    private Integer paymentId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "BookingID", nullable = false)
    private Booking booking;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "MethodID", nullable = false)
    private PaymentMethod paymentMethod;
    @NotNull @Column(precision = 10, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;
    @NotNull private LocalDateTime paymentDate;
    @NotBlank @Size(max = 10)
    private String status = "Pending";
}

