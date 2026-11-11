package com.cinema.management.model.dto.request;

import jakarta.validation.constraints.Min;
import lombok.*;

@Getter @Setter @NoArgsConstructor
public class StepRequest {
    @Min(1)
    private int step;
}

