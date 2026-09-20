package org.acme.reservation.application;

import io.smallrye.mutiny.Uni;
import org.acme.reservation.client.rental.RentalClient;
import org.acme.reservation.model.Reservation;
import org.acme.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ReservationApplicationServiceTest {

    private final ReservationRepository repository = mock(ReservationRepository.class);
    private final RentalClient rentalClient = mock(RentalClient.class);

    private final ReservationApplicationService service =
            new ReservationApplicationService(repository, rentalClient);

    @Test
    void shouldCreateReservationForAuthenticatedUser() {
        Reservation requested = reservation(10L, LocalDate.now().plusDays(2));
        Reservation persisted = reservation(10L, requested.getStartDay());
        persisted.setId(42L);
        persisted.setUserId("alice");

        when(repository.save(any(Reservation.class))).thenReturn(Uni.createFrom().item(persisted));

        Reservation result = service.create(requested, "alice").await().indefinitely();

        assertEquals(42L, result.getId());
        assertEquals("alice", result.getUserId());
        verify(repository).save(argThat(r -> "alice".equals(r.getUserId())));
        verifyNoInteractions(rentalClient);
    }

    @Test
    void shouldUseAnonymousWhenThereIsNoAuthenticatedUser() {
        Reservation requested = reservation(10L, LocalDate.now().plusDays(2));
        Reservation persisted = reservation(10L, requested.getStartDay());
        persisted.setId(43L);
        persisted.setUserId("anonymous");

        when(repository.save(any(Reservation.class))).thenReturn(Uni.createFrom().item(persisted));

        Reservation result = service.create(requested, null).await().indefinitely();

        assertEquals("anonymous", result.getUserId());
        verify(repository).save(argThat(r -> "anonymous".equals(r.getUserId())));
        verifyNoInteractions(rentalClient);
    }

    private Reservation reservation(Long carId, LocalDate startDay) {
        return Reservation.builder()
                .carId(carId)
                .startDay(startDay)
                .endDay(startDay.plusDays(5))
                .build();
    }
}
