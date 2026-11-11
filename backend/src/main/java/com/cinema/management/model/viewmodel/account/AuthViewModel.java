package com.cinema.management.model.viewmodel.account;

import com.cinema.management.validation.MinimumAge;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor
public class AuthViewModel {
    private String username = "";

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[\\W_]).{8,}$",
            message = "Mật khẩu cần ít nhất 8 ký tự, gồm 1 chữ hoa, 1 số và 1 ký tự đặc biệt")
    private String password = "";

    @NotBlank(message = "Vui lòng nhập đầy đủ họ tên")
    @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Tên không được có số hoặc ký tự đặc biệt")
    private String fullName = "";

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0")
    private String phoneNumber = "";

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không đúng định dạng")
    private String email = "";

    @NotNull(message = "Vui lòng chọn ngày sinh")
    @MinimumAge(value = 16, message = "Bạn phải đủ 16 tuổi để đăng ký")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu")
    private String confirmPassword = "";

    @AssertTrue(message = "Mật khẩu xác nhận không khớp")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}

