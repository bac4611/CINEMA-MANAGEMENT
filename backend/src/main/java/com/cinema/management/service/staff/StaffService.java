package com.cinema.management.service.staff;

import com.cinema.management.model.dto.request.SaveScheduleRequest;
import com.cinema.management.model.dto.request.StaffMovieRequest;
import com.cinema.management.model.entity.booking.Combo;
import com.cinema.management.model.viewmodel.admin.ConcessionsViewModel;
import com.cinema.management.model.viewmodel.staff.ScreeningRoomStateViewModel;
import com.cinema.management.model.viewmodel.staff.StaffHallsViewModel;
import com.cinema.management.model.viewmodel.staff.StaffScheduleViewModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StaffService {
    StaffScheduleViewModel getSchedulePage();
    List<ScheduleItem> getSchedule(LocalDate date);
    Optional<ScreeningRoomStateViewModel> getScreeningRoomState(int roomId, LocalDate date, Integer showtimeId);
    OperationResult saveSchedule(SaveScheduleRequest request);
    StaffHallsViewModel getHalls();
    ConcessionsViewModel getConcessions(Combo form);
    Optional<Combo> getConcession(int comboId);
    OperationResult createConcession(Combo combo);
    OperationResult updateConcession(Combo combo);
    OperationResult createMovie(StaffMovieRequest request);
    OperationResult updateMovie(StaffMovieRequest request);
    OperationResult deleteMovie(int movieId);

    enum Status { SUCCESS, INVALID, NOT_FOUND, CONFLICT, FAILURE }
    record OperationResult(Status status, String message, int count, int created, int updated, int deleted) {
        public static OperationResult success(String message) { return new OperationResult(Status.SUCCESS, message, 0, 0, 0, 0); }
        public static OperationResult failure(Status status, String message) { return new OperationResult(status, message, 0, 0, 0, 0); }
    }
    record ScheduleItem(int showtimeId, int movieId, String movieTitle, int roomId, int hallNumber,
                        String startTime, String endTime, long duration, LocalDate releaseDate,
                        String availableFormats, BigDecimal basePrice) { }
}
