package com.cinema.management.model.entity.theater;

import com.cinema.management.model.entity.booking.Ticket;
import com.cinema.management.model.entity.movie.Movie;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Showtimes")
@Getter @Setter @NoArgsConstructor
public class Showtime {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ShowtimeID")
    private Integer showtimeId;
    @NotNull @Column(columnDefinition = "date")
    private LocalDate date;
    @NotNull private LocalDateTime startTime;
    @NotNull private LocalDateTime endTime;
    @NotNull @Column(precision = 10, scale = 2)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MovieID", nullable = false)
    private Movie movie;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RoomID", nullable = false)
    private Room room;

    @OneToMany(mappedBy = "showtime")
    private List<Ticket> tickets = new ArrayList<>();

    @Transient
    public Integer getHallNumber() {
        return room == null ? null : room.getRoomId();
    }
}

