package com.cinema.management.model.viewmodel.account;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor
public class ResetPasswordViewModel {
    @NotNull private Integer userId;
    @NotBlank private String token = "";
    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[\\W_]).{8,}$",
            message = "Mật khẩu cần ít nhất 8 ký tự, gồm 1 chữ hoa, 1 số và 1 ký tự đặc biệt")
    private String newPassword = "";
    @NotBlank(message = "Vui lòng nhập lại mật khẩu mới")
    private String confirmPassword = "";

    @AssertTrue(message = "Mật khẩu xác nhận không khớp")
    public boolean isPasswordConfirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}

