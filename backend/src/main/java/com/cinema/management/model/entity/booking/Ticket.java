package com.cinema.management.model.entity.booking;

import com.cinema.management.model.entity.theater.Seat;
import com.cinema.management.model.entity.theater.Showtime;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "Tickets")
@Getter @Setter @NoArgsConstructor
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TicketID")
    private Integer ticketId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "BookingID", nullable = false)
    private Booking booking;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ShowtimeID", nullable = false)
    private Showtime showtime;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "SeatID", nullable = false)
    private Seat seat;
    @NotBlank @Size(max = 100)
    private String ticketCode = "";
}

