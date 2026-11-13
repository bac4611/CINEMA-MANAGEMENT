package com.cinema.management.controller.staff;

import com.cinema.management.model.dto.request.SaveScheduleRequest;
import com.cinema.management.model.dto.request.StaffMovieRequest;
import com.cinema.management.model.entity.booking.Combo;
import com.cinema.management.service.staff.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping({"/Staff", "/staff"})
public class StaffController {
    private final StaffService staff;

    public StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping({"", "/", "/Index", "/index"})
    public String index(Model model) {
        model.addAttribute("activeTab", "scheduling");
        model.addAttribute("schedule", staff.getSchedulePage());
        return "staff/index";
    }

    @GetMapping({"/GetSchedule", "/schedule"})
    @ResponseBody
    public ResponseEntity<?> getSchedule(@RequestParam LocalDate date) {
        return ResponseEntity.ok(staff.getSchedule(date));
    }

    @GetMapping({"/GetScreeningRoomState", "/screening-room-state"})
    @ResponseBody
    public ResponseEntity<?> getScreeningRoomState(@RequestParam int roomId,
                                                    @RequestParam(required = false) LocalDate date,
                                                    @RequestParam(required = false) Integer showtimeId) {
        if (roomId <= 0) return ResponseEntity.badRequest().body(Map.of("message", "Phòng chiếu không hợp lệ."));
        try {
            return staff.getScreeningRoomState(roomId, date, showtimeId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(404).body(Map.of("message", "Không tìm thấy phòng chiếu.")));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @PostMapping({"/SaveSchedule", "/save-schedule"})
    @ResponseBody
    public ResponseEntity<?> saveSchedule(@Valid @RequestBody(required = false) SaveScheduleRequest request,
                                          BindingResult errors) {
        if (request == null || errors.hasErrors()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Dữ liệu không hợp lệ.");
            body.put("errors", errors.getFieldErrors().stream().collect(
                    java.util.stream.Collectors.groupingBy(error -> error.getField(),
                            java.util.stream.Collectors.mapping(error -> error.getDefaultMessage(),
                                    java.util.stream.Collectors.toList()))));
            return ResponseEntity.badRequest().body(body);
        }
        return operation(staff.saveSchedule(request));
    }

    @GetMapping({"/Halls", "/halls"})
    public String halls(Model model) {
        model.addAttribute("activeTab", "halls");
        model.addAttribute("halls", staff.getHalls());
        return "staff/halls";
    }

    @GetMapping({"/Concessions", "/concessions"})
    public String concessions(Model model) {
        populateConcessions(model, null, null);
        return "staff/concessions";
    }

    @GetMapping({"/CreateConcession", "/create-concession"})
    public String createConcession(Model model) {
        populateConcessions(model, new Combo(), "create");
        return "staff/concessions";
    }

    @PostMapping({"/CreateConcession", "/create-concession"})
    public String createConcession(@Valid @ModelAttribute("form") Combo form, BindingResult errors,
                                   Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            populateConcessions(model, form, "create");
            return "staff/concessions";
        }
        var result = staff.createConcession(form);
        return concessionResult(result, form, "create", model, flash);
    }

    @GetMapping({"/EditConcession", "/edit-concession"})
    public String editConcession(@RequestParam int id, Model model) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Combo combo = staff.getConcession(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        populateConcessions(model, combo, "edit");
        return "staff/concessions";
    }

    @PostMapping({"/EditConcession", "/edit-concession"})
    public String editConcession(@Valid @ModelAttribute("form") Combo form, BindingResult errors,
                                 Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            populateConcessions(model, form, "edit");
            return "staff/concessions";
        }
        var result = staff.updateConcession(form);
        if (result.status() == StaffService.Status.NOT_FOUND) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return concessionResult(result, form, "edit", model, flash);
    }

    @PostMapping({"/CreateMovie", "/create-movie"})
    public String createMovie(@Valid @ModelAttribute StaffMovieRequest form, BindingResult errors,
                              RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("alertError", "Dữ liệu phim không hợp lệ.");
            return "redirect:/Staff/Index";
        }
        return movieResult(staff.createMovie(form), flash);
    }

    @PostMapping({"/EditMovie", "/edit-movie"})
    public String editMovie(@Valid @ModelAttribute StaffMovieRequest form, BindingResult errors,
                            RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("alertError", "Dữ liệu phim không hợp lệ.");
            return "redirect:/Staff/Index";
        }
        return movieResult(staff.updateMovie(form), flash);
    }

    @PostMapping({"/DeactivateMovie", "/deactivate-movie"})
    public String deactivateMovie(@RequestParam int id, RedirectAttributes flash) {
        return movieResult(staff.deleteMovie(id), flash);
    }

    private ResponseEntity<?> operation(StaffService.OperationResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", result.message()); body.put("count", result.count());
        body.put("created", result.created()); body.put("updated", result.updated()); body.put("deleted", result.deleted());
        int status = switch (result.status()) {
            case SUCCESS -> 200; case NOT_FOUND -> 404; case CONFLICT -> 409; case FAILURE -> 500; default -> 400;
        };
        return ResponseEntity.status(status).body(body);
    }

    private void populateConcessions(Model model, Combo form, String mode) {
        model.addAttribute("activeTab", "concessions");
        model.addAttribute("concessions", staff.getConcessions(form));
        if (mode != null) model.addAttribute("concessionFormMode", mode);
    }

    private String concessionResult(StaffService.OperationResult result, Combo form, String mode,
                                    Model model, RedirectAttributes flash) {
        if (result.status() == StaffService.Status.SUCCESS) {
            flash.addFlashAttribute("alertSuccess", result.message());
            return "redirect:/Staff/Concessions";
        }
        model.addAttribute("alertError", result.message());
        populateConcessions(model, form, mode);
        return "staff/concessions";
    }

    private String movieResult(StaffService.OperationResult result, RedirectAttributes flash) {
        if (result.status() == StaffService.Status.NOT_FOUND) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (result.status() == StaffService.Status.SUCCESS) flash.addFlashAttribute("alertSuccess", result.message());
        else flash.addFlashAttribute("alertError", result.message());
        return "redirect:/Staff/Index";
    }
}
