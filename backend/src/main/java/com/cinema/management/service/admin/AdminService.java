package com.cinema.management.service.admin;

import com.cinema.management.model.dto.request.AdminSettingsRequest;
import com.cinema.management.model.dto.request.CreateStaffRequest;
import com.cinema.management.model.dto.request.UpdateStaffRequest;
import com.cinema.management.model.viewmodel.admin.AdminAnalyticsViewModel;
import com.cinema.management.model.viewmodel.admin.AdminSettingsViewModel;
import com.cinema.management.model.viewmodel.admin.StaffPageViewModel;

import java.util.Optional;

public interface AdminService {
    AdminAnalyticsViewModel getAnalytics(String month, Integer movieId);
    StaffPageViewModel getStaffPage(int page);
    Optional<AdminSettingsViewModel> getSettings(int adminId);
    OperationResult updateSettings(int adminId, AdminSettingsRequest request);
    OperationResult createStaff(CreateStaffRequest request);
    OperationResult updateStaff(UpdateStaffRequest request);

    enum OperationStatus {
        SUCCESS, NOT_FOUND, DUPLICATE_EMAIL, CURRENT_PASSWORD_REQUIRED,
        INVALID_CURRENT_PASSWORD, INVALID_DATE_OF_BIRTH
    }

    record OperationResult(OperationStatus status, String message) {
        public static OperationResult success(String message) {
            return new OperationResult(OperationStatus.SUCCESS, message);
        }
    }
}
