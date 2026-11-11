package com.cinema.management.model.viewmodel.booking;
import lombok.*; import java.math.BigDecimal;
@Getter @Setter @NoArgsConstructor
public class ConcessionItemViewModel {
    private int id; private String name = ""; private BigDecimal price = BigDecimal.ZERO;
    private int stockQuantity; private int selectedQuantity;
}

