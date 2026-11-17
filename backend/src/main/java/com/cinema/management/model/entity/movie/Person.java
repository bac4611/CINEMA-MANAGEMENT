package com.cinema.management.model.entity.movie;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Persons")
@Getter @Setter @NoArgsConstructor
public class Person {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PersonID")
    private Integer personId;
    @Column(name = "FullName", nullable = false)
    private String fullName = "";
}

