package com.cinema.management.model.entity.account;

import com.cinema.management.model.entity.booking.Booking;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserID")
    private Integer userId;

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
    private String fullName = "";

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 200)
    @Column(unique = true)
    private String email = "";

    @Size(max = 512)
    private String passwordHash;

    @Size(max = 15)
    private String phoneNumber;

    @Column(name = "DOB")
    private LocalDate dateOfBirth;
    private boolean status = true;
    private boolean emailConfirmed;

    @Size(max = 50) private String externalProvider;
    @Size(max = 200) private String externalProviderKey;
    @Size(max = 64) private String emailVerificationTokenHash;
    private Instant emailVerificationTokenExpiresAt;
    private Instant emailVerificationLastSentAt;
    @Size(max = 64) private String passwordResetTokenHash;
    private Instant passwordResetTokenExpiresAt;
    private Instant passwordResetLastSentAt;

    @NotBlank @Size(max = 20)
    private String role = "USER";

    @OneToMany(mappedBy = "user")
    private List<Booking> bookings = new ArrayList<>();
}

