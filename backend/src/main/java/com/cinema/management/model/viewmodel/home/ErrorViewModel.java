package com.cinema.management.model.viewmodel.home;

import lombok.*;

@Getter @Setter @NoArgsConstructor
public class ErrorViewModel {
    private String requestId;

    public boolean isShowRequestId() {
        return requestId != null && !requestId.isBlank();
    }
}

