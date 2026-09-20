package org.acme.users.adapter.out.reservation.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class Reservation {
    private Long id;
    private String userId;
    private Long carId;
    private LocalDate startDay;
    private LocalDate endDay;
    private String status;
}
