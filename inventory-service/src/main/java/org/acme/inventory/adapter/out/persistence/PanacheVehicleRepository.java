package org.acme.inventory.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Vehicle;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheVehicleRepository implements VehicleRepository {

    private final EntityManager entityManager;

    @Inject
    public PanacheVehicleRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<Vehicle> findAll() {
        return entityManager.createQuery(
                        "select v from VehicleEntity v order by v.id",
                        VehicleEntity.class)
                .getResultList()
                .stream()
                .map(VehicleMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Vehicle> findByLicensePlate(LicensePlate licensePlate) {
        return entityManager.createQuery(
                        "select v from VehicleEntity v where v.licensePlateNumber = :plate",
                        VehicleEntity.class)
                .setParameter("plate", licensePlate.value())
                .getResultStream()
                .findFirst()
                .map(VehicleMapper::toDomain);
    }

    @Override
    @Transactional
    public Vehicle save(Vehicle vehicle) {
        VehicleEntity entity = VehicleMapper.toEntity(vehicle);
        if (entity.id == null) {
            entityManager.persist(entity);
            return VehicleMapper.toDomain(entity);
        }
        return VehicleMapper.toDomain(entityManager.merge(entity));
    }
}
