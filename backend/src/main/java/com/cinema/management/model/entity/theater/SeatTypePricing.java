package com.cinema.management.model.entity.theater;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "SeatTypePricing")
@Getter @Setter @NoArgsConstructor
public class SeatTypePricing {
    @Id @Size(max = 10)
    private String seatType = "";
    @NotNull @Column(precision = 4, scale = 2)
    private BigDecimal multiplier = BigDecimal.ONE;

    @OneToMany(mappedBy = "seatTypePricing")
    private List<Seat> seats = new ArrayList<>();
}

