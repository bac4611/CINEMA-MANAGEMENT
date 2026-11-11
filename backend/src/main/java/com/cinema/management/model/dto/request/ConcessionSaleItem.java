package com.cinema.management.model.dto.request;

import jakarta.validation.constraints.Min;
import lombok.*;

@Getter @Setter @NoArgsConstructor
public class ConcessionSaleItem {
    private int comboId;
    @Min(1)
    private int quantity;
}

