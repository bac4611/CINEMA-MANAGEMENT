package com.cinema.management.model.viewmodel.admin;

import java.time.LocalDate;
import java.util.List;

public record StaffPageViewModel(List<StaffItem> staff, long totalCount,
                                 int currentPage, int totalPages, int pageSize) {
    public record StaffItem(Integer userId, String fullName, String email,
                            String phoneNumber, LocalDate dateOfBirth, boolean status) { }
}
