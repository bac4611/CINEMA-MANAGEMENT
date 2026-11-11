package com.cinema.management.model.dto.request;

import jakarta.validation.Valid;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class ConcessionSaleRequest {
    @Valid
    private List<ConcessionSaleItem> items = new ArrayList<>();
}

