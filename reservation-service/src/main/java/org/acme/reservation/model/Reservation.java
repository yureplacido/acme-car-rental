package org.acme.reservation.model;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    private Long id;
    private Long carId;
    private LocalDate startDay;
    private LocalDate endDay;
    private String userId;

    /**
     * Check if the given duration overlaps with this reservation
     *
     * @return true if the dates overlap with the reservation, false
     * otherwise
     */
    public boolean isReserved(LocalDate startDay, LocalDate endDay) {
        return (!(this.endDay.isBefore(startDay) ||
                this.startDay.isAfter(endDay)));
    }
}