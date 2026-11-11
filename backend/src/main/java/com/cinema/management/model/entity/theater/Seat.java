package com.cinema.management.model.entity.theater;

import com.cinema.management.model.entity.booking.Ticket;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Seats")
@Getter @Setter @NoArgsConstructor
public class Seat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SeatID")
    private Integer seatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RoomID", nullable = false)
    private Room room;
    @NotBlank @Size(max = 10)
    private String seatCode = "";
    @NotBlank @Size(max = 10)
    private String seatType = "";
    @NotBlank @Size(max = 50)
    private String seatStatus = "Not Order";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SeatType", referencedColumnName = "seatType", insertable = false, updatable = false)
    private SeatTypePricing seatTypePricing;

    @OneToMany(mappedBy = "seat")
    private List<Ticket> tickets = new ArrayList<>();
}

