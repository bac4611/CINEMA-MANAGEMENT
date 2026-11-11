package com.cinema.management.model.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email = "";
    @NotBlank(message = "Mật khẩu không được để trống")
    private String password = "";
    private boolean rememberMe;
}

