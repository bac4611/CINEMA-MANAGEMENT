package com.cinema.management.model.dto.request;

import jakarta.validation.Valid;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class SelectConcessionsRequest {
    @Valid
    private List<ConcessionRequest> items = new ArrayList<>();
}

