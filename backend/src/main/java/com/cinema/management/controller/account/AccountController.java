package com.cinema.management.controller.account;

import com.cinema.management.model.dto.request.LoginRequest;
import com.cinema.management.model.dto.request.ProfileRequest;
import com.cinema.management.model.viewmodel.account.AuthViewModel;
import com.cinema.management.model.viewmodel.account.ForgotPasswordViewModel;
import com.cinema.management.model.viewmodel.account.ResetPasswordViewModel;
import com.cinema.management.service.account.AccountService;
import com.cinema.management.service.authentication.UserSignInService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.function.BiFunction;

/** MVC endpoints. Business rules, persistence, password hashing and email delivery live in AccountService. */
@Controller
@RequestMapping({"/Account", "/account"})
public class AccountController {
    private final AccountService accounts;
    private final UserSignInService signIn;
    private final Validator validator;

    public AccountController(AccountService accounts, UserSignInService signIn, Validator validator) {
        this.accounts = accounts;
        this.signIn = signIn;
        this.validator = validator;
    }

    @GetMapping({"/Login", "/login"})
    public String login(Model model) {
        model.addAttribute("login", new LoginRequest());
        populateAuthView(model, null);
        return "account/login";
    }

    @PostMapping({"/Login", "/login"})
    public String login(@ModelAttribute("login") LoginRequest form, BindingResult errors,
                        Model model, HttpServletRequest request, HttpServletResponse response,
                        RedirectAttributes flash) {
        boolean passwordless = accounts.isDevelopmentPasswordlessLogin(form.getEmail());
        validator.validate(form).forEach(error -> {
            String field = error.getPropertyPath().toString();
            if (!(passwordless && "password".equals(field))) {
                errors.rejectValue(field, "invalid", error.getMessage());
            }
        });
        populateAuthView(model, null);
        if (errors.hasErrors()) {
            return "account/login";
        }
        var result = accounts.authenticate(form, true);
        if (result.status() != AccountService.LoginStatus.SUCCESS) {
            String message = switch (result.status()) {
                case INACTIVE -> "Tài khoản đã bị khóa.";
                case EMAIL_UNCONFIRMED -> "Vui lòng xác minh email trước khi đăng nhập.";
                default -> "Sai email hoặc mật khẩu.";
            };
            errors.reject("login.failed", message);
            model.addAttribute("alertError", message);
            if (result.status() == AccountService.LoginStatus.EMAIL_UNCONFIRMED) {
                populateAuthView(model, result.user().getEmail());
            }
            return "account/login";
        }
        signIn.signIn(request, response, result.user(), form.isRememberMe());
        flash.addFlashAttribute("alertSuccess", "Đăng nhập thành công! Xin chào " + result.user().getFullName());
        return switch (result.user().getRole()) {
            case "Admin" -> "redirect:/Admin/Index";
            case "Staff" -> "redirect:/Staff/Index";
            default -> "redirect:/Home/Index";
        };
    }

    @PostMapping({"/Logout", "/logout"})
    public String logout(HttpServletRequest request, RedirectAttributes flash) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        flash.addFlashAttribute("alertSuccess", "Bạn đã đăng xuất thành công.");
        return "redirect:/Account/Login";
    }

    @GetMapping({"/AccessDenied", "/access-denied"})
    public String accessDenied() {
        return "account/access-denied";
    }

    @GetMapping({"/Register", "/register"})
    public String register(Model model) {
        model.addAttribute("register", new AuthViewModel());
        populateAuthView(model, null);
        return "account/register";
    }

    @PostMapping({"/Register", "/register"})
    public String register(@Valid @ModelAttribute("register") AuthViewModel form, BindingResult errors,
                           Model model, RedirectAttributes flash, HttpServletRequest request) {
        populateAuthView(model, null);
        if (errors.hasErrors()) {
            return "account/register";
        }
        var result = accounts.register(form, linkBuilder(request, "ConfirmEmail"));
        switch (result.status()) {
            case DUPLICATE_ACCOUNT -> {
                errors.rejectValue("email", "account.duplicate", "Tài khoản đã tồn tại.");
                return "account/register";
            }
            case FAILED -> {
                model.addAttribute("alertError", "Chưa tạo được tài khoản, vui lòng thử lại.");
                return "account/register";
            }
            case CREATED -> flash.addFlashAttribute("alertSuccess", "Đăng ký thành công. Vui lòng kiểm tra email xác minh.");
            case VERIFICATION_EMAIL_FAILED -> flash.addFlashAttribute("alertError",
                    "Tài khoản đã được tạo nhưng chưa gửi được email xác minh. Vui lòng thử gửi lại.");
        }
        return verificationSent(result.email(), flash);
    }

    @GetMapping({"/EmailVerificationSent", "/email-verification-sent"})
    public String emailVerificationSent(@RequestParam(defaultValue = "") String email, Model model) {
        model.addAttribute("email", email);
        return "account/email-verification-sent";
    }

    @GetMapping({"/ForgotPassword", "/forgot-password"})
    public String forgotPassword(Model model) {
        model.addAttribute("forgotPassword", new ForgotPasswordViewModel());
        return "account/forgot-password";
    }

    @PostMapping({"/ForgotPassword", "/forgot-password"})
    public String forgotPassword(@Valid @ModelAttribute("forgotPassword") ForgotPasswordViewModel form,
                                 BindingResult errors, HttpServletRequest request) {
        if (errors.hasErrors()) {
            return "account/forgot-password";
        }
        accounts.forgotPassword(form.getEmail(), linkBuilder(request, "ResetPassword"));
        return "redirect:/Account/ForgotPasswordConfirmation";
    }

    @GetMapping({"/ForgotPasswordConfirmation", "/forgot-password-confirmation"})
    public String forgotPasswordConfirmation() {
        return "account/forgot-password-confirmation";
    }

    @GetMapping({"/ResetPassword", "/reset-password"})
    public String resetPassword(@RequestParam Integer userId,
                                @RequestParam(defaultValue = "") String token, Model model) {
        var form = new ResetPasswordViewModel();
        form.setUserId(userId);
        form.setToken(token);
        model.addAttribute("resetPassword", form);
        if (!accounts.isPasswordResetTokenValid(userId, token)) {
            var errors = new BeanPropertyBindingResult(form, "resetPassword");
            errors.reject("token.invalid", "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "resetPassword", errors);
        }
        return "account/reset-password";
    }

    @PostMapping({"/ResetPassword", "/reset-password"})
    public String resetPassword(@Valid @ModelAttribute("resetPassword") ResetPasswordViewModel form,
                                BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return "account/reset-password";
        }
        if (!accounts.resetPassword(form)) {
            errors.reject("token.invalid", "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
            return "account/reset-password";
        }
        flash.addFlashAttribute("alertSuccess", "Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.");
        return "redirect:/Account/ResetPasswordConfirmation";
    }

    @GetMapping({"/ResetPasswordConfirmation", "/reset-password-confirmation"})
    public String resetPasswordConfirmation() {
        return "account/reset-password-confirmation";
    }

    @GetMapping({"/ConfirmEmail", "/confirm-email"})
    public String confirmEmail(@RequestParam Integer userId, @RequestParam(defaultValue = "") String token,
                               RedirectAttributes flash) {
        boolean confirmed = accounts.confirmEmail(userId, token);
        flash.addFlashAttribute(confirmed ? "alertSuccess" : "alertError", confirmed
                ? "Xác minh email thành công. Bạn có thể đăng nhập."
                : "Liên kết xác minh email không hợp lệ hoặc đã hết hạn.");
        return "redirect:/Account/Login";
    }

    @PostMapping({"/ResendVerificationEmail", "/resend-verification-email"})
    public String resendVerificationEmail(@RequestParam(defaultValue = "") String email,
                                          HttpServletRequest request, RedirectAttributes flash) {
        var result = accounts.resendVerificationEmail(email, linkBuilder(request, "ConfirmEmail"));
        String message = switch (result.status()) {
            case MISSING_EMAIL -> "Vui lòng nhập email để gửi lại xác minh.";
            case USER_NOT_FOUND -> "Nếu email tồn tại, hệ thống sẽ gửi lại email xác minh.";
            case ALREADY_CONFIRMED -> "Email này đã được xác minh. Bạn có thể đăng nhập.";
            case INACTIVE -> "Tài khoản của bạn đã bị khóa.";
            case COOLDOWN -> "Vui lòng đợi một chút trước khi gửi lại email xác minh.";
            case SENT -> "Đã gửi lại email xác minh. Vui lòng kiểm tra hộp thư.";
            case FAILED -> "Chưa gửi được email xác minh. Vui lòng thử lại sau.";
        };
        boolean success = switch (result.status()) {
            case SENT, USER_NOT_FOUND, ALREADY_CONFIRMED -> true;
            default -> false;
        };
        flash.addFlashAttribute(success ? "alertSuccess" : "alertError", message);
        return switch (result.status()) {
            case MISSING_EMAIL, USER_NOT_FOUND, ALREADY_CONFIRMED, INACTIVE -> "redirect:/Account/Login";
            default -> verificationSent(result.email(), flash);
        };
    }

    @GetMapping({"/Profile", "/profile"})
    public String profile(HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        Integer userId = currentUserId(request);
        if (userId == null) {
            return "redirect:/Account/Login";
        }
        var profile = accounts.loadProfile(userId).orElse(null);
        if (profile == null) {
            return "redirect:/Account/Login";
        }
        var form = new ProfileRequest();
        form.setFullName(profile.fullName());
        form.setEmail(profile.email());
        form.setPhoneNumber(profile.phoneNumber());
        form.setDateOfBirth(profile.dateOfBirth());
        model.addAttribute("profile", form);
        model.addAttribute("user", profile);
        return "account/profile";
    }

    @PostMapping({"/Profile", "/profile"})
    public String profile(@Valid @ModelAttribute("profile") ProfileRequest form, BindingResult errors,
                          HttpServletRequest request, HttpServletResponse response, Model model,
                          RedirectAttributes flash) {
        response.setHeader("Cache-Control", "no-store");
        Integer userId = currentUserId(request);
        if (userId == null) {
            return "redirect:/Account/Login";
        }
        if (errors.hasErrors()) {
            model.addAttribute("user", accounts.loadProfile(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
            model.addAttribute("alertError", "Vui lòng kiểm tra thông tin hồ sơ.");
            return "account/profile";
        }
        if (!accounts.updateProfile(userId, form)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        request.getSession().setAttribute("UserFullName", form.getFullName().trim());
        flash.addFlashAttribute("successMessage", "Cập nhật thông tin thành công.");
        return "redirect:/Account/Profile";
    }

    private Integer currentUserId(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && session.getAttribute("UserID") instanceof Integer id ? id : null;
    }

    private void populateAuthView(Model model, String email) {
        model.addAttribute("showDevelopmentPasswordlessLoginMessage", accounts.isDevelopment());
        model.addAttribute("googleLoginEnabled", accounts.isGoogleLoginEnabled());
        model.addAttribute("unconfirmedEmail", email);
    }

    private String verificationSent(String email, RedirectAttributes flash) {
        flash.addAttribute("email", email);
        return "redirect:/Account/EmailVerificationSent";
    }

    private BiFunction<Integer, String, String> linkBuilder(HttpServletRequest request, String action) {
        String base = ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
        return (userId, token) -> org.springframework.web.util.UriComponentsBuilder.fromUriString(base)
                .path("/Account/" + action).queryParam("userId", userId).queryParam("token", token)
                .build().encode().toUriString();
    }
}
