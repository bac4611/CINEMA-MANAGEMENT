package com.cinema.management.model.entity.theater;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Rooms")
@Getter @Setter @NoArgsConstructor
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RoomID")
    private Integer roomId;
    @NotBlank @Size(max = 100)
    private String roomName = "";

    @OneToMany(mappedBy = "room")
    private List<Seat> seats = new ArrayList<>();
    @OneToMany(mappedBy = "room")
    private List<Showtime> showtimes = new ArrayList<>();
}

