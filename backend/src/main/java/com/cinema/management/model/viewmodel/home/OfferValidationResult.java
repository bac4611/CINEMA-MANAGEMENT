package com.cinema.management.model.viewmodel.home;
import lombok.*;
@Getter @Setter @NoArgsConstructor
public class OfferValidationResult {
    private boolean valid; private String status = "invalid"; private String message = "";
    private OfferViewModel offer;
}

