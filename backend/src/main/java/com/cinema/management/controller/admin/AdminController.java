package com.cinema.management.controller.admin;

import com.cinema.management.model.dto.request.AdminSettingsRequest;
import com.cinema.management.model.dto.request.CreateStaffRequest;
import com.cinema.management.model.dto.request.UpdateStaffRequest;
import com.cinema.management.service.admin.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping({"/Admin", "/admin"})
public class AdminController {
    private final AdminService admins;

    public AdminController(AdminService admins) {
        this.admins = admins;
    }

    @InitBinder
    public void trimFormStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @ModelAttribute
    public void disableCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping({"", "/", "/Index", "/index"})
    public String index(HttpServletRequest request, Model model) {
        Object email = request.getSession().getAttribute("UserEmail");
        model.addAttribute("adminEmail", email == null ? "Admin" : email);
        return "admin/index";
    }

    @GetMapping({"/Analytics", "/analytics"})
    public String analytics(@RequestParam(required = false) String month,
                            @RequestParam(required = false) Integer movieId, Model model) {
        model.addAttribute("analytics", admins.getAnalytics(month, movieId));
        return "admin/analytics";
    }

    @GetMapping({"/Staff", "/staff"})
    public String staff(@RequestParam(defaultValue = "1") int page, Model model) {
        populateStaffPage(page, model);
        model.addAttribute("createStaff", new CreateStaffRequest());
        model.addAttribute("updateStaff", new UpdateStaffRequest());
        return "admin/staff";
    }

    @GetMapping({"/Settings", "/settings"})
    public String settings(HttpServletRequest request, Model model, RedirectAttributes flash) {
        Integer userId = currentUserId(request);
        if (userId == null) {
            return "redirect:/Account/Login";
        }
        var settings = admins.getSettings(userId).orElse(null);
        if (settings == null) {
            flash.addFlashAttribute("errorMessage", "Không tìm thấy tài khoản Admin.");
            return "redirect:/Admin/Index";
        }
        AdminSettingsRequest form = new AdminSettingsRequest();
        form.setFullName(settings.fullName());
        form.setEmail(settings.email());
        model.addAttribute("settings", form);
        model.addAttribute("admin", settings);
        return "admin/settings";
    }

    @PostMapping({"/UpdateSettings", "/update-settings"})
    public String updateSettings(@Valid @ModelAttribute("settings") AdminSettingsRequest form,
                                 BindingResult errors, HttpServletRequest request,
                                 Model model, RedirectAttributes flash) {
        Integer userId = currentUserId(request);
        if (userId == null) {
            return "redirect:/Account/Login";
        }
        if (errors.hasErrors()) {
            model.addAttribute("admin", admins.getSettings(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
            return "admin/settings";
        }
        var result = admins.updateSettings(userId, form);
        if (result.status() != AdminService.OperationStatus.SUCCESS) {
            model.addAttribute("errorMessage", result.message());
            model.addAttribute("admin", admins.getSettings(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
            return "admin/settings";
        }
        request.getSession().setAttribute("UserEmail", form.getEmail().trim().toLowerCase());
        flash.addFlashAttribute("successMessage", result.message());
        return "redirect:/Admin/Settings";
    }

    @PostMapping({"/CreateStaff", "/create-staff"})
    public String createStaff(@Valid @ModelAttribute("createStaff") CreateStaffRequest form,
                              BindingResult errors, @RequestParam(defaultValue = "1") int page,
                              Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            var result = admins.createStaff(form);
            if (result.status() == AdminService.OperationStatus.SUCCESS) {
                flash.addFlashAttribute("successMessage", result.message());
                flash.addAttribute("page", page);
                return "redirect:/Admin/Staff";
            }
            rejectOperation(errors, result);
        }
        model.addAttribute("openModal", "create");
        model.addAttribute("updateStaff", new UpdateStaffRequest());
        populateStaffPage(page, model);
        return "admin/staff";
    }

    @PostMapping({"/UpdateStaff", "/update-staff"})
    public String updateStaff(@Valid @ModelAttribute("updateStaff") UpdateStaffRequest form,
                              BindingResult errors, @RequestParam(defaultValue = "1") int page,
                              Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            var result = admins.updateStaff(form);
            if (result.status() == AdminService.OperationStatus.SUCCESS) {
                flash.addFlashAttribute("successMessage", result.message());
                flash.addAttribute("page", page);
                return "redirect:/Admin/Staff";
            }
            rejectOperation(errors, result);
        }
        model.addAttribute("openModal", "edit");
        model.addAttribute("createStaff", new CreateStaffRequest());
        populateStaffPage(page, model);
        return "admin/staff";
    }

    private void populateStaffPage(int page, Model model) {
        var result = admins.getStaffPage(page);
        model.addAttribute("staff", result.staff());
        model.addAttribute("totalCount", result.totalCount());
        model.addAttribute("currentPage", result.currentPage());
        model.addAttribute("totalPages", result.totalPages());
        model.addAttribute("pageSize", result.pageSize());
    }

    private void rejectOperation(BindingResult errors, AdminService.OperationResult result) {
        String field = switch (result.status()) {
            case DUPLICATE_EMAIL -> "email";
            case INVALID_DATE_OF_BIRTH -> "dateOfBirth";
            case NOT_FOUND -> "userId";
            default -> null;
        };
        if (field == null) {
            errors.reject("admin.operation.failed", result.message());
        } else {
            errors.rejectValue(field, "admin.operation.failed", result.message());
        }
    }

    private Integer currentUserId(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && session.getAttribute("UserID") instanceof Integer id ? id : null;
    }
}
