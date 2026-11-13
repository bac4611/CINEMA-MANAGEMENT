package com.cinema.management.controller.account;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.service.authentication.GoogleAuthService;
import com.cinema.management.service.authentication.UserSignInService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping({"/GoogleAuth", "/google-auth"})
public class GoogleAuthController {
    private static final String RETURN_URL = "GoogleAuthReturnUrl";

    private final GoogleAuthService google;
    private final UserSignInService signIn;

    public GoogleAuthController(GoogleAuthService google, UserSignInService signIn) {
        this.google = google;
        this.signIn = signIn;
    }

    @PostMapping({"/Login", "/login"})
    public String login(@RequestParam(required = false) String returnUrl,
                        HttpServletRequest request, RedirectAttributes flash) {
        if (!google.isEnabled()) {
            flash.addFlashAttribute("alertError", "Đăng nhập Google chưa được cấu hình.");
            return "redirect:/Account/Login";
        }
        var session = request.getSession(true);
        if (isLocalUrl(returnUrl)) session.setAttribute(RETURN_URL, returnUrl);
        else session.removeAttribute(RETURN_URL);
        return "redirect:/oauth2/authorization/google";
    }

    @GetMapping({"/Callback", "/callback"})
    public String callback(@RequestParam(required = false) String remoteError,
                           @org.springframework.security.core.annotation.AuthenticationPrincipal OAuth2User principal,
                           HttpServletRequest request, HttpServletResponse response,
                           RedirectAttributes flash) {
        if (remoteError != null && !remoteError.isBlank()) {
            clearTemporaryAuthentication(request, response);
            flash.addFlashAttribute("alertError", "Đăng nhập Google thất bại. Vui lòng thử lại.");
            return "redirect:/Account/Login";
        }
        if (principal == null) {
            clearTemporaryAuthentication(request, response);
            flash.addFlashAttribute("alertError", "Không đọc được thông tin đăng nhập Google.");
            return "redirect:/Account/Login";
        }
        var result = google.authenticate(principal.getAttributes());
        if (result.status() != GoogleAuthService.Status.SUCCESS || result.user() == null) {
            clearTemporaryAuthentication(request, response);
            flash.addFlashAttribute("alertError", result.message());
            return "redirect:/Account/Login";
        }
        User user = result.user();
        String returnUrl = sessionValue(request, RETURN_URL);
        signIn.signIn(request, response, user, true);
        request.getSession().removeAttribute(RETURN_URL);
        flash.addFlashAttribute("alertSuccess", "Đăng nhập Google thành công. Xin chào " + user.getFullName() + ".");
        if (isLocalUrl(returnUrl)) return "redirect:" + returnUrl;
        return switch (user.getRole()) {
            case "Admin" -> "redirect:/Admin/Index";
            case "Staff" -> "redirect:/Staff/Index";
            default -> "redirect:/Home/Index";
        };
    }

    private void clearTemporaryAuthentication(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        var empty = SecurityContextHolder.createEmptyContext();
        new HttpSessionSecurityContextRepository().saveContext(empty, request, response);
        var session = request.getSession(false);
        if (session != null) session.removeAttribute(RETURN_URL);
    }

    private String sessionValue(HttpServletRequest request, String key) {
        var session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(key);
        return value == null ? null : value.toString();
    }

    private boolean isLocalUrl(String value) {
        return value != null && value.startsWith("/") && !value.startsWith("//")
                && !value.startsWith("/\\");
    }
}
