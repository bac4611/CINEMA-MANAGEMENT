package com.cinema.management.model.entity.booking;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.model.entity.movie.Movie;
import com.cinema.management.model.entity.theater.Showtime;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "BookingHolds")
@Getter @Setter @NoArgsConstructor
public class BookingHold {
    @Id @Column(name = "BookingHoldID")
    private UUID bookingHoldId;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "MovieID", nullable = false)
    private Movie movie;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ShowtimeID")
    private Showtime showtime;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "UserID")
    private User user;

    @NotBlank @Size(max = 20)
    private String status = "Pending";
    private Instant createdAtUtc;
    private Instant expiresAtUtc;
    @Version private byte[] rowVersion;

    @OneToMany(mappedBy = "bookingHold")
    private List<BookingSeatHold> seatHolds = new ArrayList<>();
    @OneToMany(mappedBy = "bookingHold")
    private List<BookingComboHold> comboHolds = new ArrayList<>();
}

