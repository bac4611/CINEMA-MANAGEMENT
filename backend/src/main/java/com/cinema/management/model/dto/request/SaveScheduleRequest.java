package com.cinema.management.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class SaveScheduleRequest {
    @NotNull private LocalDate date;
    @NotNull private List<@Valid SaveScheduleItemRequest> items = new ArrayList<>();
}
