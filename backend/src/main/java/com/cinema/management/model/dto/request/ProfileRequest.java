package com.cinema.management.model.dto.request;

import com.cinema.management.validation.MinimumAge;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor
public class ProfileRequest {
    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 50, message = "Họ tên tối đa 50 ký tự")
    @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Họ tên chỉ được chứa chữ cái và khoảng trắng")
    private String fullName;

    @NotBlank @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại phải có 10 chữ số và bắt đầu bằng 0")
    private String phoneNumber;

    @MinimumAge(value = 16, message = "Bạn phải đủ 16 tuổi để cập nhật hồ sơ")
    private LocalDate dateOfBirth;
}
