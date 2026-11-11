package com.cinema.management.model.entity.movie;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Countries")
@Getter @Setter @NoArgsConstructor
public class Country {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CountryID")
    private Integer countryId;
    @Column(nullable = false)
    private String countryName = "";
}

