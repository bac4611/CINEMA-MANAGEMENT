package com.cinema.management.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor
public class CreateStaffRequest {
    @NotBlank(message = "Tên không được để trống.")
    @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Tên chỉ được chứa chữ cái và khoảng trắng.")
    private String fullName;

    @NotBlank(message = "Email không được để trống.")
    @Email(message = "Email không đúng định dạng.")
    private String email;

    @Pattern(regexp = "^$|^0[0-9]{9}$",
            message = "Số điện thoại phải bắt đầu bằng 0 và có 10 chữ số.")
    private String phoneNumber;

    @NotBlank(message = "Mật khẩu không được để trống.")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[\\W_]).{8,}$",
            message = "Mật khẩu cần ít nhất 8 ký tự, gồm chữ hoa, số và ký tự đặc biệt.")
    private String password;

    private LocalDate dateOfBirth;
}
