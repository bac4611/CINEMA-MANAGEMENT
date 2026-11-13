package com.cinema.management.model.entity.payment;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.booking.Booking;
import com.cinema.management.model.entity.booking.BookingHold;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.theater.Showtime;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "PaymentIntents")
@Getter @Setter @NoArgsConstructor
public class PaymentIntent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PaymentIntentID")
    private Integer paymentIntentId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "UserID", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "MovieID", nullable = false)
    private Movie movie;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ShowtimeID", nullable = false)
    private Showtime showtime;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "BookingID")
    private Booking booking;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "BookingHoldID")
    private BookingHold bookingHold;

    @NotBlank @Size(max = 100)
    @Column(unique = true)
    private String paymentReference = "";
    @NotNull @Column(precision = 10, scale = 2)
    private BigDecimal expectedAmount = BigDecimal.ZERO;
    @NotBlank @Size(max = 20)
    private String status = "Pending";
    @NotBlank @Size(max = 500)
    private String selectedSeatCodes = "";
    @Lob private String selectedCombosJson;
    @Column(unique = true)
    private Long sePayTransactionId;
    @Size(max = 255) private String sePayReferenceCode;
    @Lob private String sePayContent;
    @Lob private String webhookPayload;
    private Instant createdAtUtc = Instant.now();
    private Instant expiresAtUtc;
    private Instant paidAtUtc;
}

