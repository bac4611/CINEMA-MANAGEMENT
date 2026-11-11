package com.cinema.management.model.dto.request;

import lombok.*;

@Getter @Setter @NoArgsConstructor
public class SelectShowtimeRequest {
    private int movieId;
    private int showtimeId;
    private String format = "2D";
    private String offerCode;
}

