package com.cinema.management.model.entity.movie;

import com.cinema.management.model.entity.account.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Reviews")
@Getter @Setter @NoArgsConstructor
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ReviewID")
    private Integer reviewId;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "UserID", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "MovieID", nullable = false)
    private Movie movie;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ParentReviewID")
    private Review parentReview;

    @NotBlank @Size(max = 2000)
    @Column(name = "Content", nullable = false)
    private String content = "";
    @DecimalMin("0.00") @DecimalMax("5.00")
    @Column(precision = 3, scale = 2)
    private BigDecimal rating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @NotBlank @Size(max = 20)
    private String status = "Visible";
}

