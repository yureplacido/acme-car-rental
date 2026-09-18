package org.acme.reservation.client.rental;

import java.time.LocalDate;
import lombok.Value;

@Value
public class Rental {

    Long id;
    String userId;
    Long reservationId;
    LocalDate startDate;
}