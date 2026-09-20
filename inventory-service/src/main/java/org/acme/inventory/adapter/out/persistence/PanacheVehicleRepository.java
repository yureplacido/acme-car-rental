package org.acme.inventory.adapter.out.persistence;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Vehicle;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheVehicleRepository implements VehicleRepository, PanacheRepository<VehicleEntity> {

    @Override
    @WithSession
    public Uni<List<Vehicle>> all() {
        return listAll()
                .map(items -> items.stream()
                        .map(VehicleMapper::toDomain)
                        .toList());
    }

    @Override
    @WithSession
    public Uni<Optional<Vehicle>> findByLicensePlate(LicensePlate licensePlate) {
        return find("licensePlateNumber", licensePlate.value())
                .firstResult()
                .map(entity -> Optional.ofNullable(entity).map(VehicleMapper::toDomain));
    }

    @Override
    @WithTransaction
    public Uni<Vehicle> save(Vehicle vehicle) {
        VehicleEntity entity = VehicleMapper.toEntity(vehicle);
        if (entity.id == null) {
            return persist(entity).map(VehicleMapper::toDomain);
        }

        return findById(entity.id)
                .onItem().ifNull().failWith(
                        () -> new IllegalStateException("Vehicle " + entity.id + " not found"))
                .invoke(existing -> VehicleMapper.copy(entity, existing))
                .call(ignored -> flush())
                .map(VehicleMapper::toDomain);
    }
}