package com.cinema.management.controller.account;

import com.cinema.management.model.dto.request.LoginRequest;
import com.cinema.management.model.dto.response.AccountLoginResponse;
import com.cinema.management.service.account.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountApiController {
    private final AccountService accounts;

    public AccountApiController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping({"/login", "/api/account/login"})
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        var result = accounts.authenticate(request, false);
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
