package com.cinema.management.controller.account;

import com.cinema.management.model.dto.request.LoginRequest;
import com.cinema.management.model.dto.request.RegisterRequest;
import com.cinema.management.model.dto.response.AccountLoginResponse;
import com.cinema.management.model.dto.response.AccountRegistrationResponse;
import com.cinema.management.service.account.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class AccountApiController {
    private final AccountService accounts;

    public AccountApiController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/api/account/register")
    public ResponseEntity<AccountRegistrationResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        String baseUrl = ServletUriComponentsBuilder.fromContextPath(servletRequest)
                .build().toUriString();
        var result = accounts.register(request.toViewModel(), (userId, token) ->
                org.springframework.web.util.UriComponentsBuilder.fromUriString(baseUrl)
                        .path("/Account/ConfirmEmail")
                        .queryParam("userId", userId)
                        .queryParam("token", token)
                        .build().encode().toUriString());

        return switch (result.status()) {
            case CREATED -> ResponseEntity.status(201).body(new AccountRegistrationResponse(
                    "CREATED", result.email(),
                    accounts.isDevelopment()
                            ? "Đăng ký thành công. Tài khoản đã được xác minh tự động trong môi trường dev."
                            : "Đăng ký thành công. Vui lòng kiểm tra email xác minh."));
            case VERIFICATION_EMAIL_FAILED -> ResponseEntity.status(201).body(new AccountRegistrationResponse(
                    "VERIFICATION_EMAIL_FAILED", result.email(),
                    "Tài khoản đã được tạo nhưng chưa gửi được email xác minh."));
            case DUPLICATE_ACCOUNT -> ResponseEntity.status(409).body(new AccountRegistrationResponse(
                    "DUPLICATE_ACCOUNT", result.email(), "Tài khoản đã tồn tại."));
            case FAILED -> ResponseEntity.internalServerError().body(new AccountRegistrationResponse(
                    "FAILED", result.email(), "Chưa tạo được tài khoản, vui lòng thử lại."));
        };
    }

    @PostMapping({"/login", "/api/account/login"})
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        // Passwordless login is only effective for the three allow-listed accounts in the dev profile.
        var result = accounts.authenticate(request, accounts.isDevelopment());
        if (result.status() != AccountService.LoginStatus.SUCCESS) {
            String message = switch (result.status()) {
                case INACTIVE -> "Tài khoản đã bị khóa";
                case EMAIL_UNCONFIRMED -> "Tài khoản chưa xác minh email";
                default -> "Sai email hoặc mật khẩu";
            };
            return ResponseEntity.status(401).body(message);
        }
        return ResponseEntity.ok(AccountLoginResponse.of(accounts.generateAccessToken(result.user()), result.user()));
    }

    @GetMapping("/api/account/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            int userId = Integer.parseInt(jwt.getSubject());
            return ResponseEntity.ok(new AccountLoginResponse.UserInfo(userId, jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("email"), jwt.getClaimAsString("role")));
        } catch (NumberFormatException exception) {
            return ResponseEntity.status(401).build();
        }
    }
}
