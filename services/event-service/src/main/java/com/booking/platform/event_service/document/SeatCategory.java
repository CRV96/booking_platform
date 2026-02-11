package com.booking.platform.event_service.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatCategory {

    private String name;
    private Double price;
    private String currency;
    private Integer totalSeats;
    private Integer availableSeats;
}
