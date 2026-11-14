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
    @Column(name = "FullName")
    private String fullName = "";

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 200)
    @Column(name = "Email", unique = true)
    private String email = "";

    @Size(max = 512)
    @Column(name = "PasswordHash")
    private String passwordHash;

    @Size(max = 15)
    @Column(name = "PhoneNumber")
    private String phoneNumber;

    @Column(name = "DOB")
    private LocalDate dateOfBirth;
    @Column(name = "Status")
    private boolean status = true;
    @Column(name = "EmailConfirmed")
    private boolean emailConfirmed;

    @Size(max = 50) @Column(name = "ExternalProvider") private String externalProvider;
    @Size(max = 200) @Column(name = "ExternalProviderKey") private String externalProviderKey;
    @Size(max = 64) @Column(name = "EmailVerificationTokenHash") private String emailVerificationTokenHash;
    @Column(name = "EmailVerificationTokenExpiresAt") private Instant emailVerificationTokenExpiresAt;
    @Column(name = "EmailVerificationLastSentAt") private Instant emailVerificationLastSentAt;
    @Size(max = 64) @Column(name = "PasswordResetTokenHash") private String passwordResetTokenHash;
    @Column(name = "PasswordResetTokenExpiresAt") private Instant passwordResetTokenExpiresAt;
    @Column(name = "PasswordResetLastSentAt") private Instant passwordResetLastSentAt;

    @NotBlank @Size(max = 20)
    @Column(name = "Role")
    private String role = "USER";

    @OneToMany(mappedBy = "user")
    private List<Booking> bookings = new ArrayList<>();
}

