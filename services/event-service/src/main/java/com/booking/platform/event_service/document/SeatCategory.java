package com.booking.platform.event_service.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Embedded document for a seat category (e.g. "VIP", "General Admission") with pricing and availability. */
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
