package com.cinema.management.model.entity.booking;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.payment.Payment;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Bookings")
@Getter @Setter @NoArgsConstructor
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BookingID")
    private Integer bookingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;
    @NotNull private LocalDateTime bookingDate;
    @NotNull @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @NotBlank @Size(max = 10)
    private String status = "Pending";

    @OneToMany(mappedBy = "booking")
    private List<Ticket> tickets = new ArrayList<>();
    @OneToMany(mappedBy = "booking")
    private List<BookingCombo> bookingCombos = new ArrayList<>();
    @OneToMany(mappedBy = "booking")
    private List<Payment> payments = new ArrayList<>();
}

