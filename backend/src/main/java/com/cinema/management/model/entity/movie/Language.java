package com.cinema.management.model.entity.movie;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Languages")
@Getter @Setter @NoArgsConstructor
public class Language {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LanguageID")
    private Integer languageId;
    @Column(nullable = false)
    private String languageName = "";
}

