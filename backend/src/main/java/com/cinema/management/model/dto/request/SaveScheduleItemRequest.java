package com.cinema.management.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class SaveScheduleItemRequest {
    private int showtimeId;
    @Min(1) private int movieId;
    @Min(1) private int roomId;
    @NotBlank private String startTime = "";
    private String endTime = "";
}
