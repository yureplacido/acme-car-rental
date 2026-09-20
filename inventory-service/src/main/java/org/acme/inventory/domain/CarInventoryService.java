package org.acme.inventory.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.inventory.model.graphql.Car;
import org.acme.inventory.model.graphql.CarStatus;
import org.acme.inventory.repository.CarRepository;

import java.util.List;
import java.util.Optional;

/**
 * Camada de aplicação/domínio do inventário — o único core compartilhado entre os
 * adapters de transporte GraphQL ({@code api.GraphQLInventoryService}) e gRPC
 * ({@code grpc.GrpcInventoryService}). Encapsula as regras de domínio do carro:
 * status padrão na entrada e ciclo de vida com soft delete (nunca apagar do banco,
 * pois reservas de outro serviço referenciam o id — consistência eventual).
 */
@ApplicationScoped
public class CarInventoryService {

    private final CarRepository carRepository;

    @Inject
    public CarInventoryService(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    public List<Car> all() {
        return carRepository.all();
    }

    public Optional<Car> findByPlate(String licensePlateNumber) {
        return carRepository.findByLicensePlateNumberOptional(licensePlateNumber);
    }

    /** Cadastra um veículo; o status vira {@link CarStatus#AVAILABLE} quando não informado. */
    @Transactional
    public Car register(Car car) {
        if (car.getStatus() == null) {
            car.setStatus(CarStatus.AVAILABLE);
        }
        return carRepository.save(car);
    }

    /**
     * Soft delete: marca o veículo como {@link CarStatus#DECOMMISSIONED} e devolve-o,
     * ou vazio quando a placa não existe. A linha nunca é apagada.
     */
    @Transactional
    public Optional<Car> decommission(String licensePlateNumber) {
        Optional<Car> car = carRepository.findByLicensePlateNumberOptional(licensePlateNumber);
        car.ifPresent(found -> {
            found.setStatus(CarStatus.DECOMMISSIONED);
            carRepository.save(found);
        });
        return car;
    }
}