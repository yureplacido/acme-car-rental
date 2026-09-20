package org.acme.inventory.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Vehicle;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheVehicleRepository implements VehicleRepository, PanacheRepository<VehicleEntity> {

    @Override
    public List<Vehicle> findAll() {
        return listAll().stream().map(VehicleMapper::toDomain).toList();
    }

    @Override
    public Optional<Vehicle> findByLicensePlate(LicensePlate licensePlate) {
        return find("licensePlateNumber", licensePlate.value())
                .firstResultOptional()
                .map(VehicleMapper::toDomain);
    }

    @Override
    @Transactional
    public Vehicle save(Vehicle vehicle) {
        VehicleEntity entity = VehicleMapper.toEntity(vehicle);
        if (entity.id == null) {
            persist(entity);
            return VehicleMapper.toDomain(entity);
        }
        return VehicleMapper.toDomain(getEntityManager().merge(entity));
    }
}
